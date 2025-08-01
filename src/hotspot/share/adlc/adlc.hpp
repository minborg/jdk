/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_ADLC_ADLC_HPP
#define SHARE_ADLC_ADLC_HPP

//
// Standard include file for ADLC parser
//

#include <ctype.h>
// standard library constants
#include <iostream>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

/* Make sure that we have the intptr_t and uintptr_t definitions */
#ifdef _WIN32

using namespace std;

#define strdup _strdup

#ifndef _INTPTR_T_DEFINED
#ifdef _WIN64
typedef __int64 intptr_t;
#else
#error "Unsupported platform"
#endif
#define _INTPTR_T_DEFINED
#endif

#ifndef _UINTPTR_T_DEFINED
#ifdef _WIN64
typedef unsigned __int64 uintptr_t;
#else
#error "Unsupported platform"
#endif
#define _UINTPTR_T_DEFINED
#endif

#endif // _WIN32

#if defined(LINUX) || defined(_ALLBSD_SOURCE)
  #include <inttypes.h>
#endif // LINUX || _ALLBSD_SOURCE

// Macros
#define uint32 unsigned int
#define uint   unsigned int

// VM components
#include "opto/opcodes.hpp"

// Macros
// Debugging note:  Put a breakpoint on "abort".
#undef assert
#define assert(cond, msg) { if (!(cond)) { fprintf(stderr, "assert fails %s %d: %s\n", __FILE__, __LINE__, msg); abort(); }}
#undef max
#define max(a, b)   (((a)>(b)) ? (a) : (b))

// ADLC components
#include "adlArena.hpp"
#include "adlparse.hpp"
#include "archDesc.hpp"

// globally define ArchDesc for convenience.  Alternatively every form
// could have a backpointer to the AD but it's too complicated to pass
// it everywhere it needs to be available.
extern ArchDesc* globalAD;

// Performs snprintf and asserts the result is non-negative (so there was not
// an encoding error) and that the output was not truncated.
extern int snprintf_checked(char* buf, size_t len, const char* fmt, ...);

#endif // SHARE_ADLC_ADLC_HPP
