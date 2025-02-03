# Pooled Memory Allocation

## Summary

Introduce an API for pooled memory allocation, which provides recyclable native memory.
Pooled memory allocation offer improved allocation performance over the existing FFM arenas. This is a preview API.

## Goals
 * Improve the performance of applications doing repetitive memory segment allocations.
 * Reduce the burden on the operating system under which such applications run.
 * Provide implementations that are efficient and scalable, even for virtual threads.
 * Allow for improved performance when using certain system calls with captures states, e.g. involving `errno` handling.

## Non-goals
 * It is not a goal to introduce pooled memory that can be shared across threads akin to `Arena.ofShared()`,
 * It is not a goal to make invocation of system calls like `malloc()` and `free()` faster.
 * It is not a goal to introduce stack-based allocation.

## Motivation

When working with native code, we often have to allocate temporary memory. The following example shows a 
method in C that takes two strings and outputs the concatenation of the strings to the standard output:

```c 
void print( char* first, char* second )
{
    int first_len = strlen(first);
    int second_len = strlen(second);
    char *ccat = (char*) malloc (first_len + second_len + 1); 
    
    strcpy(ccat, first);
    strcat(ccat + first_len, second);
    
    write(STDOUT_FILENO, ccat, sizeof(ccat) - 1);
    free(ccat);
}
```
Here, we first compute the length of the two strings and then `malloc()` a memory region to hold the concatenation
of the strings plus a null terminator held by the `ccat` variable. We then copy the `first` string into the
`ccat`region and then we append the `second` string to the `ccat` region. Finally, the resulting concatenation
is written to `stdout` using the `write()` function, after which the `ccat` region is freed again. Code using fixed 
sized `struct` objects can also allocate temporary instances _on the stack_, vastly improving memory allocation
performance compared to `malloc()`/`free()` constructs. Stack allocation is not available in Java. 

In Java's Foreign Function & Memory API (FFM), working with temporarily allocated native memory segments often entails
creating a new `Arena` from which the segments are allocated. When the segments are no longer needed, the `Arena`
is closed and the allocated segments are subsequently freed. Under the covers, an `Arena` is using variants of the system
calls `malloc()` and `free()` to handle native memory.

This idiom is illustrated in the following example where we do the equivalent of the above C program and where
the `print()` method takes two memory segments that contains the characters of a `first` and a `second` string including
null termination:

```java
void print(MemorySegment first, MemorySegment second) {
    try (var arena = Arena.ofConfined()) {
        MemorySegment ccat = arena.allocate(first.byteSize() + second.byteSize() - 1);
        ccat.copyFrom(first);
        MemorySegment.copy(second, 0, ccat, first.byteSize() - 1, second.byteSize());
        write(STDOUT_FILENO, ccat, ccat.byteSize() - 1);
    }
}
```

First, we create a new confined `Arena` in a try-with-resource block, allocates a new segment `ccat`
in which we then write the concatenation of the two strings, calls a function `write()` (that is linked to the corresponding
native `write()` system call) and then finally disposes of the temporary `ccat` segment by means of automatically closing
the `Arena` upon exiting the try-with-resource block.

Since new native memory is allocated and freed upon every invocation via the operating system, the VM needs to
transition from Java to native code two times extra per invocation (i.e. when first calling `malloc()` and then `free()`).
Even though `malloc()` and `free()` are highly optimized, there is also a certain degree of memory 
allocation overhead imposed on the operating system.

This will make the Java application slow, inefficient, and will increase the burden on the operating system under which 
the application runs.

Another very similar class of problematic constructs are certain system calls linked to Java via the `java.lang.foreign.Linker` 
which requires a temporarily small segment when being invoked. Said segment can, for example, be used to capture 
an `errno` value (e.g. for `fopen()` and `close()`), whereby the `errno` can be written into the segment,
should an error occur. Typically, allocation needs to be done upon every invocation -- effectively taxing every such 
system call with the burden of native memory allocation and deallocation.

On the surface, this problem might appear to be solvable using a `ThreadLocal` variable holding `MemorySegment` 
instances of some size. However, efficiency and performance quickly dwindles in applications with a large number of
virtual threads. Another problem with such a scheme is that the application occasionally might want to allocate more
memory than in the `ThreadLocal`, and so extra provisions have to be put in to handle such cases. Finally, it is 
non-trivial to ensure thread local variables are not accidentally shared across invocations (e.g. upon recursion) or 
remains lingering after a thread has died.

### Toward pooled memory allocation

What we are missing is a way to safely pool and reuse allocated native memory within the JDK, effectively
eliminating the burden on the application and the operating system when dealing with
small temporarily allocated segments. Here is an example that outlines a solution to this:

```java
    var allocator = ... // Associated with pooled memory
    try {
        MemorySegment segment = allocator.allocate(...); // Use pooled memory
        // Use the segment
    } finaly {
       // Recycle pooled memory
       close(allocator);    
    }   
```

## Description

A pooled arena is an object of type `PooledArena`, which holds recyclable memory of a certain size
which can be sliced and reused indefinitely and where resulting memory segments are re-assigned to new arenas
as arenas are created and used.

Here is an example showing the use of a `PooledArena` solving the problems described in the Motivation section above:

```java
private static final ArenaPool ARENA_POOL = ArenaPool.create(32);
...
void print(MemorySegment first, MemorySegment second) {
    try (var arena = ARENA_POOL.take()) {
        MemorySegment ccat = arena.allocate(first.byteSize() + second.byteSize() - 1);
        ccat.copyFrom(first);
        MemorySegment.copy(second, 0, ccat, first.byteSize() - 1, second.byteSize());
        write(STDOUT_FILENO, ccat, ccat.byteSize() - 1);
    }
}
```

First, an arena pool (with recyclable memory chunks of size `32`) is declared and instantiated via the `static` 
field `ARENA_POOL`. A pooled arena is thread-safe. This means, any number of threads can `take()` new `Arena` objects
concurrently without the recyclable memory being accidentally shared across threads. Also, content of the recyclable memory
can neither be observed by another thread nor by the same thread using any `Arena` created from the pool. It is worth
mentioning, the `Arena` objects themselves are never recycled as opposed to the memory allocated from them. 

Second, in the try-with-resource block; a new arena is created which, when segments are allocated, initially
will return recyclable memory which are carved and wrapped in a memory segment and re-associated with the new arena's
life cycle. When the `ccat` segment has been created, we copy the content of the `first` and `second` segment into it.
After the `ccat` segment is consumed by the `write()` method, the try-with-resource block will exit and the `arena`
is closed automatically upon which any memory that originates from the recyclable memory will be recycled and returned 
back to the pool. If all recyclable memory in the pool, available to the thread, is used up or if the fixed size of 
the recyclable memory in the arena pool is insufficient, new segments are allocated/freed on demand using an underlying
fall-back allocator, allowing for graceful degradation on over use.

This means application allocation performance can improve significantly while upholding safety requirements and the 
burden on the operating system can be reduced. Notably, pooled arenas can also be used directly in conjunction with
system calls involving captured states like `errno`.

*This is a preview API, disabled by default*
To use the Pooled Arenas API, you must enable preview features:

Compile your program with javac --release 25 --enable-preview Main.java, and run it with java --enable-preview Main; or,

When using the source code launcher, run your program with java --enable-preview Main.java; or

When using jshell, start it with jshell --enable-preview.

## Alternatives
Use `ThreadLocal` memory segments which is hard to get right for reasons described above. 

## Risks and assumptions
Implementing arena pools that are efficient for Virtual Threads is non-trivial.
