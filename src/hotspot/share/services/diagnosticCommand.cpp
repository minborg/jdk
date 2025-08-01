/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderHierarchyDCmd.hpp"
#include "classfile/classLoaderStats.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "code/codeCache.hpp"
#include "compiler/compilationMemoryStatistic.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/directivesParser.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "jvm.h"
#include "memory/metaspace/metaspaceDCmd.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memMapPrinter.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtDCmd.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiAgentList.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/vmOperations.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/diagnosticFramework.hpp"
#include "services/heapDumper.hpp"
#include "services/management.hpp"
#include "services/writeableFlags.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/parseInteger.hpp"
#ifdef LINUX
#include "mallocInfoDcmd.hpp"
#include "os_posix.hpp"
#include "trimCHeapDCmd.hpp"

#include <errno.h>
#endif

static void loadAgentModule(TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  JavaValue result(T_OBJECT);
  Handle h_module_name = java_lang_String::create_from_str("jdk.management.agent", CHECK);
  JavaCalls::call_static(&result,
                         vmClasses::module_Modules_klass(),
                         vmSymbols::loadModule_name(),
                         vmSymbols::loadModule_signature(),
                         h_module_name,
                         THREAD);
}

void DCmd::register_dcmds(){
  // Registration of the diagnostic commands
  // First argument specifies which interfaces will export the command
  // Second argument specifies if the command is enabled
  // Third  argument specifies if the command is hidden
  uint32_t full_export = DCmd_Source_Internal | DCmd_Source_AttachAPI
                         | DCmd_Source_MBean;
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<HelpDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VersionDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CommandLineDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<PrintSystemPropertiesDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<PrintVMFlagsDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SetVMFlagDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VMDynamicLibrariesDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VMUptimeDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VMInfoDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SystemGCDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<RunFinalizationDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<HeapInfoDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<FinalizerInfoDCmd>(full_export, true, false));
#if INCLUDE_SERVICES
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<HeapDumpDCmd>(DCmd_Source_Internal | DCmd_Source_AttachAPI, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassHistogramDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SystemDictionaryDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassHierarchyDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassesDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SymboltableDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<StringtableDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<metaspace::MetaspaceDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<EventLogDCmd>(full_export, true, false));
#if INCLUDE_JVMTI // Both JVMTI and SERVICES have to be enabled to have this dcmd
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JVMTIAgentLoadDCmd>(full_export, true, false));
#endif // INCLUDE_JVMTI
#endif // INCLUDE_SERVICES
#if INCLUDE_JVMTI
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JVMTIDataDumpDCmd>(full_export, true, false));
#endif // INCLUDE_JVMTI
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ThreadDumpDCmd>(full_export, true, false));
#if INCLUDE_JVMTI
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ThreadDumpToFileDCmd>(full_export, true, false));
#endif // INCLUDE_JVMTI
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VThreadSchedulerDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VThreadPollersDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassLoaderStatsDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassLoaderHierarchyDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompileQueueDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CodeListDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CodeCacheDCmd>(full_export, true, false));
#ifdef LINUX
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<PerfMapDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<TrimCLibcHeapDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<MallocInfoDcmd>(full_export, true, false));
#endif // LINUX
#if defined(LINUX) || defined(_WIN64) || defined(__APPLE__)
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SystemMapDCmd>(full_export, true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SystemDumpMapDCmd>(full_export, true,false));
#endif // LINUX or WINDOWS or MacOS
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CodeHeapAnalyticsDCmd>(full_export, true, false));

  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompilerDirectivesPrintDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompilerDirectivesAddDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompilerDirectivesRemoveDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompilerDirectivesClearDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CompilationMemoryStatisticDCmd>(full_export, true, false));

  // Enhanced JMX Agent Support
  // These commands not currently exported via the DiagnosticCommandMBean
  uint32_t jmx_agent_export_flags = DCmd_Source_Internal | DCmd_Source_AttachAPI;
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStartRemoteDCmd>(jmx_agent_export_flags, true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStartLocalDCmd>(jmx_agent_export_flags, true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStopRemoteDCmd>(jmx_agent_export_flags, true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStatusDCmd>(jmx_agent_export_flags, true,false));

#if INCLUDE_CDS
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<DumpSharedArchiveDCmd>(full_export, true, false));
#endif // INCLUDE_CDS

  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<NMTDCmd>(full_export, true, false));
}

HelpDCmd::HelpDCmd(outputStream* output, bool heap) : DCmdWithParser(output, heap),
  _all("-all", "Show help for all commands", "BOOLEAN", false, "false"),
  _cmd("command name", "The name of the command for which we want help",
        "STRING", false) {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_argument(&_cmd);
};


static int compare_strings(const char** s1, const char** s2) {
  return ::strcmp(*s1, *s2);
}

void HelpDCmd::execute(DCmdSource source, TRAPS) {
  if (_all.value()) {
    GrowableArray<const char*>* cmd_list = DCmdFactory::DCmd_list(source);
    cmd_list->sort(compare_strings);
    for (int i = 0; i < cmd_list->length(); i++) {
      DCmdFactory* factory = DCmdFactory::factory(source, cmd_list->at(i),
                                                  strlen(cmd_list->at(i)));
      output()->print_cr("%s%s", factory->name(),
                         factory->is_enabled() ? "" : " [disabled]");
      output()->print_cr("\t%s", factory->description());
      output()->cr();
      factory = factory->next();
    }
  } else if (_cmd.has_value()) {
    DCmd* cmd = nullptr;
    DCmdFactory* factory = DCmdFactory::factory(source, _cmd.value(),
                                                strlen(_cmd.value()));
    if (factory != nullptr) {
      output()->print_cr("%s%s", factory->name(),
                         factory->is_enabled() ? "" : " [disabled]");
      output()->print_cr("%s", factory->description());
      output()->print_cr("\nImpact: %s", factory->impact());
      output()->cr();
      cmd = factory->create_resource_instance(output());
      if (cmd != nullptr) {
        DCmdMark mark(cmd);
        cmd->print_help(factory->name());
      }
    } else {
      output()->print_cr("Help unavailable : '%s' : No such command", _cmd.value());
    }
  } else {
    output()->print_cr("The following commands are available:");
    GrowableArray<const char *>* cmd_list = DCmdFactory::DCmd_list(source);
    cmd_list->sort(compare_strings);
    for (int i = 0; i < cmd_list->length(); i++) {
      DCmdFactory* factory = DCmdFactory::factory(source, cmd_list->at(i),
                                                  strlen(cmd_list->at(i)));
      output()->print_cr("%s%s", factory->name(),
                         factory->is_enabled() ? "" : " [disabled]");
      factory = factory->_next;
    }
    output()->print_cr("\nFor more information about a specific command use 'help <command>'.");
  }
}

void VersionDCmd::execute(DCmdSource source, TRAPS) {
  output()->print_cr("%s version %s", VM_Version::vm_name(),
          VM_Version::vm_release());
  JDK_Version jdk_version = JDK_Version::current();
  if (jdk_version.patch_version() > 0) {
    output()->print_cr("JDK %d.%d.%d.%d", jdk_version.major_version(),
            jdk_version.minor_version(), jdk_version.security_version(),
            jdk_version.patch_version());
  } else {
    output()->print_cr("JDK %d.%d.%d", jdk_version.major_version(),
            jdk_version.minor_version(), jdk_version.security_version());
  }
}

PrintVMFlagsDCmd::PrintVMFlagsDCmd(outputStream* output, bool heap) :
                                   DCmdWithParser(output, heap),
  _all("-all", "Print all flags supported by the VM", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_all);
}

void PrintVMFlagsDCmd::execute(DCmdSource source, TRAPS) {
  if (_all.value()) {
    JVMFlag::printFlags(output(), true);
  } else {
    JVMFlag::printSetFlags(output());
  }
}

SetVMFlagDCmd::SetVMFlagDCmd(outputStream* output, bool heap) :
                                   DCmdWithParser(output, heap),
  _flag("flag name", "The name of the flag we want to set",
        "STRING", true),
  _value("string value", "The value we want to set", "STRING", false) {
  _dcmdparser.add_dcmd_argument(&_flag);
  _dcmdparser.add_dcmd_argument(&_value);
}

void SetVMFlagDCmd::execute(DCmdSource source, TRAPS) {
  const char* val = nullptr;
  if (_value.value() != nullptr) {
    val = _value.value();
  }

  FormatBuffer<80> err_msg("%s", "");
  int ret = WriteableFlags::set_flag(_flag.value(), val, JVMFlagOrigin::MANAGEMENT, err_msg);

  if (ret != JVMFlag::SUCCESS) {
    output()->print_cr("%s", err_msg.buffer());
  }
}

void JVMTIDataDumpDCmd::execute(DCmdSource source, TRAPS) {
  if (JvmtiExport::should_post_data_dump()) {
    JvmtiExport::post_data_dump();
  }
}

#if INCLUDE_SERVICES
#if INCLUDE_JVMTI
JVMTIAgentLoadDCmd::JVMTIAgentLoadDCmd(outputStream* output, bool heap) :
                                       DCmdWithParser(output, heap),
  _libpath("library path", "Absolute path of the JVMTI agent to load.",
           "STRING", true),
  _option("agent option", "Option string to pass the agent.", "STRING", false) {
  _dcmdparser.add_dcmd_argument(&_libpath);
  _dcmdparser.add_dcmd_argument(&_option);
}

void JVMTIAgentLoadDCmd::execute(DCmdSource source, TRAPS) {

  if (_libpath.value() == nullptr) {
    output()->print_cr("JVMTI.agent_load dcmd needs library path.");
    return;
  }

  char *suffix = strrchr(_libpath.value(), '.');
  bool is_java_agent = (suffix != nullptr) && (strncmp(".jar", suffix, 4) == 0);

  if (is_java_agent) {
    if (_option.value() == nullptr) {
      JvmtiAgentList::load_agent("instrument", false, _libpath.value(), output());
    } else {
      size_t opt_len = strlen(_libpath.value()) + strlen(_option.value()) + 2;
      if (opt_len > 4096) {
        output()->print_cr("JVMTI agent attach failed: Options is too long.");
        return;
      }

      char *opt = (char *)os::malloc(opt_len, mtInternal);
      if (opt == nullptr) {
        output()->print_cr("JVMTI agent attach failed: "
                           "Could not allocate %zu bytes for argument.",
                           opt_len);
        return;
      }

      jio_snprintf(opt, opt_len, "%s=%s", _libpath.value(), _option.value());
      JvmtiAgentList::load_agent("instrument", false, opt, output());

      os::free(opt);
    }
  } else {
    JvmtiAgentList::load_agent(_libpath.value(), true, _option.value(), output());
  }
}

#endif // INCLUDE_JVMTI
#endif // INCLUDE_SERVICES

void PrintSystemPropertiesDCmd::execute(DCmdSource source, TRAPS) {
  // load VMSupport
  Symbol* klass = vmSymbols::jdk_internal_vm_VMSupport();
  Klass* k = SystemDictionary::resolve_or_fail(klass, true, CHECK);
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->should_be_initialized()) {
    ik->initialize(THREAD);
  }
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output());
    output()->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // invoke the serializePropertiesToByteArray method
  JavaValue result(T_OBJECT);
  JavaCallArguments args;

  Symbol* signature = vmSymbols::void_byte_array_signature();
  JavaCalls::call_static(&result,
                         ik,
                         vmSymbols::serializePropertiesToByteArray_name(),
                         signature,
                         &args,
                         THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output());
    output()->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // The result should be a [B
  oop res = result.get_oop();
  assert(res->is_typeArray(), "just checking");
  assert(TypeArrayKlass::cast(res->klass())->element_type() == T_BYTE, "just checking");

  // copy the bytes to the output stream
  typeArrayOop ba = typeArrayOop(res);
  jbyte* addr = typeArrayOop(res)->byte_at_addr(0);
  output()->print_raw((const char*)addr, ba->length());
}

VMUptimeDCmd::VMUptimeDCmd(outputStream* output, bool heap) :
                           DCmdWithParser(output, heap),
  _date("-date", "Add a prefix with current date", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_date);
}

void VMUptimeDCmd::execute(DCmdSource source, TRAPS) {
  if (_date.value()) {
    output()->date_stamp(true, "", ": ");
  }
  output()->time_stamp().update_to(tty->time_stamp().ticks());
  output()->stamp();
  output()->print_cr(" s");
}

void VMInfoDCmd::execute(DCmdSource source, TRAPS) {
  VMError::print_vm_info(_output);
}

void SystemGCDCmd::execute(DCmdSource source, TRAPS) {
  Universe::heap()->collect(GCCause::_dcmd_gc_run);
}

void RunFinalizationDCmd::execute(DCmdSource source, TRAPS) {
  Klass* k = vmClasses::System_klass();
  JavaValue result(T_VOID);
  JavaCalls::call_static(&result, k,
                         vmSymbols::run_finalization_name(),
                         vmSymbols::void_method_signature(), CHECK);
}

void HeapInfoDCmd::execute(DCmdSource source, TRAPS) {
  MutexLocker hl(THREAD, Heap_lock);
  Universe::heap()->print_heap_on(output());
}

void FinalizerInfoDCmd::execute(DCmdSource source, TRAPS) {
  ResourceMark rm(THREAD);

  if (!InstanceKlass::is_finalization_enabled()) {
    output()->print_cr("Finalization is disabled");
    return;
  }

  Klass* k = SystemDictionary::resolve_or_fail(
    vmSymbols::finalizer_histogram_klass(), true, CHECK);

  JavaValue result(T_ARRAY);

  // We are calling lang.ref.FinalizerHistogram.getFinalizerHistogram() method
  // and expect it to return array of FinalizerHistogramEntry as Object[]

  JavaCalls::call_static(&result, k,
                         vmSymbols::get_finalizer_histogram_name(),
                         vmSymbols::void_finalizer_histogram_entry_array_signature(), CHECK);

  objArrayOop result_oop = (objArrayOop) result.get_oop();
  if (result_oop->length() == 0) {
    output()->print_cr("No instances waiting for finalization found");
    return;
  }

  oop foop = result_oop->obj_at(0);
  InstanceKlass* ik = InstanceKlass::cast(foop->klass());

  fieldDescriptor count_fd, name_fd;

  Klass* count_res = ik->find_field(
    vmSymbols::finalizer_histogram_entry_count_field(), vmSymbols::int_signature(), &count_fd);

  Klass* name_res = ik->find_field(
    vmSymbols::finalizer_histogram_entry_name_field(), vmSymbols::string_signature(), &name_fd);

  assert(count_res != nullptr && name_res != nullptr, "Unexpected layout of FinalizerHistogramEntry");

  output()->print_cr("Unreachable instances waiting for finalization");
  output()->print_cr("#instances  class name");
  output()->print_cr("-----------------------");

  for (int i = 0; i < result_oop->length(); ++i) {
    oop element_oop = result_oop->obj_at(i);
    oop str_oop = element_oop->obj_field(name_fd.offset());
    char *name = java_lang_String::as_utf8_string(str_oop);
    int count = element_oop->int_field(count_fd.offset());
    output()->print_cr("%10d  %s", count, name);
  }
}

#if INCLUDE_SERVICES // Heap dumping/inspection supported
HeapDumpDCmd::HeapDumpDCmd(outputStream* output, bool heap) :
                           DCmdWithParser(output, heap),
  _filename("filename","Name of the dump file", "FILE",true),
  _all("-all", "Dump all objects, including unreachable objects",
       "BOOLEAN", false, "false"),
  _gzip("-gz", "If specified, the heap dump is written in gzipped format "
               "using the given compression level. 1 (recommended) is the fastest, "
               "9 the strongest compression.", "INT", false, "1"),
  _overwrite("-overwrite", "If specified, the dump file will be overwritten if it exists",
           "BOOLEAN", false, "false"),
  _parallel("-parallel", "Number of parallel threads to use for heap dump. The VM "
                          "will try to use the specified number of threads, but might use fewer.",
            "INT", false, "1") {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_argument(&_filename);
  _dcmdparser.add_dcmd_option(&_gzip);
  _dcmdparser.add_dcmd_option(&_overwrite);
  _dcmdparser.add_dcmd_option(&_parallel);
}

void HeapDumpDCmd::execute(DCmdSource source, TRAPS) {
  jlong level = -1; // -1 means no compression.
  jlong parallel = HeapDumper::default_num_of_dump_threads();

  if (_gzip.is_set()) {
    level = _gzip.value();

    if (level < 1 || level > 9) {
      output()->print_cr("Compression level out of range (1-9): " JLONG_FORMAT, level);
      return;
    }
  }

  if (_parallel.is_set()) {
    parallel = _parallel.value();

    if (parallel < 0) {
      output()->print_cr("Invalid number of parallel dump threads.");
      return;
    } else if (parallel == 0) {
      // 0 implies to disable parallel heap dump, in such case, we use serial dump instead
      parallel = 1;
    }
  }

  // Request a full GC before heap dump if _all is false
  // This helps reduces the amount of unreachable objects in the dump
  // and makes it easier to browse.
  HeapDumper dumper(!_all.value() /* request GC if _all is false*/);
  dumper.dump(_filename.value(), output(), (int) level, _overwrite.value(), (uint)parallel);
}

ClassHistogramDCmd::ClassHistogramDCmd(outputStream* output, bool heap) :
                                       DCmdWithParser(output, heap),
  _all("-all", "Inspect all objects, including unreachable objects",
       "BOOLEAN", false, "false"),
  _parallel_thread_num("-parallel",
       "Number of parallel threads to use for heap inspection. "
       "0 (the default) means let the VM determine the number of threads to use. "
       "1 means use one thread (disable parallelism). "
       "For any other value the VM will try to use the specified number of "
       "threads, but might use fewer.",
       "INT", false, "0") {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_option(&_parallel_thread_num);
}

void ClassHistogramDCmd::execute(DCmdSource source, TRAPS) {
  jlong num = _parallel_thread_num.value();
  if (num < 0) {
    output()->print_cr("Parallel thread number out of range (>=0): " JLONG_FORMAT, num);
    return;
  }
  uint parallel_thread_num = num == 0
      ? MAX2<uint>(1, (uint)os::initial_active_processor_count() * 3 / 8)
      : num;
  VM_GC_HeapInspection heapop(output(),
                              !_all.value(), /* request full gc if false */
                              parallel_thread_num);
  VMThread::execute(&heapop);
}

#endif // INCLUDE_SERVICES

ThreadDumpDCmd::ThreadDumpDCmd(outputStream* output, bool heap) :
                               DCmdWithParser(output, heap),
  _locks("-l", "print java.util.concurrent locks", "BOOLEAN", false, "false"),
  _extended("-e", "print extended thread information", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_locks);
  _dcmdparser.add_dcmd_option(&_extended);
}

void ThreadDumpDCmd::execute(DCmdSource source, TRAPS) {
  // thread stacks and JNI global handles
  VM_PrintThreads op1(output(), _locks.value(), _extended.value(), true /* print JNI handle info */);
  VMThread::execute(&op1);

  // Deadlock detection
  VM_FindDeadlocks op2(output());
  VMThread::execute(&op2);
}

// Enhanced JMX Agent support

JMXStartRemoteDCmd::JMXStartRemoteDCmd(outputStream *output, bool heap_allocated) :

  DCmdWithParser(output, heap_allocated),

  _config_file
  ("config.file",
   "set com.sun.management.config.file", "STRING", false),

  _jmxremote_host
  ("jmxremote.host",
   "set com.sun.management.jmxremote.host", "STRING", false),

  _jmxremote_port
  ("jmxremote.port",
   "set com.sun.management.jmxremote.port", "STRING", false),

  _jmxremote_rmi_port
  ("jmxremote.rmi.port",
   "set com.sun.management.jmxremote.rmi.port", "STRING", false),

  _jmxremote_ssl
  ("jmxremote.ssl",
   "set com.sun.management.jmxremote.ssl", "STRING", false),

  _jmxremote_registry_ssl
  ("jmxremote.registry.ssl",
   "set com.sun.management.jmxremote.registry.ssl", "STRING", false),

  _jmxremote_authenticate
  ("jmxremote.authenticate",
   "set com.sun.management.jmxremote.authenticate", "STRING", false),

  _jmxremote_password_file
  ("jmxremote.password.file",
   "set com.sun.management.jmxremote.password.file", "STRING", false),

  _jmxremote_access_file
  ("jmxremote.access.file",
   "set com.sun.management.jmxremote.access.file", "STRING", false),

  _jmxremote_login_config
  ("jmxremote.login.config",
   "set com.sun.management.jmxremote.login.config", "STRING", false),

  _jmxremote_ssl_enabled_cipher_suites
  ("jmxremote.ssl.enabled.cipher.suites",
   "set com.sun.management.jmxremote.ssl.enabled.cipher.suite", "STRING", false),

  _jmxremote_ssl_enabled_protocols
  ("jmxremote.ssl.enabled.protocols",
   "set com.sun.management.jmxremote.ssl.enabled.protocols", "STRING", false),

  _jmxremote_ssl_need_client_auth
  ("jmxremote.ssl.need.client.auth",
   "set com.sun.management.jmxremote.need.client.auth", "STRING", false),

  _jmxremote_ssl_config_file
  ("jmxremote.ssl.config.file",
   "set com.sun.management.jmxremote.ssl.config.file", "STRING", false),

// JDP Protocol support
  _jmxremote_autodiscovery
  ("jmxremote.autodiscovery",
   "set com.sun.management.jmxremote.autodiscovery", "STRING", false),

   _jdp_port
  ("jdp.port",
   "set com.sun.management.jdp.port", "INT", false),

   _jdp_address
  ("jdp.address",
   "set com.sun.management.jdp.address", "STRING", false),

   _jdp_source_addr
  ("jdp.source_addr",
   "set com.sun.management.jdp.source_addr", "STRING", false),

   _jdp_ttl
  ("jdp.ttl",
   "set com.sun.management.jdp.ttl", "INT", false),

   _jdp_pause
  ("jdp.pause",
   "set com.sun.management.jdp.pause", "INT", false),

   _jdp_name
  ("jdp.name",
   "set com.sun.management.jdp.name", "STRING", false)

  {
    _dcmdparser.add_dcmd_option(&_config_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_host);
    _dcmdparser.add_dcmd_option(&_jmxremote_port);
    _dcmdparser.add_dcmd_option(&_jmxremote_rmi_port);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl);
    _dcmdparser.add_dcmd_option(&_jmxremote_registry_ssl);
    _dcmdparser.add_dcmd_option(&_jmxremote_authenticate);
    _dcmdparser.add_dcmd_option(&_jmxremote_password_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_access_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_login_config);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_enabled_cipher_suites);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_enabled_protocols);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_need_client_auth);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_config_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_autodiscovery);
    _dcmdparser.add_dcmd_option(&_jdp_port);
    _dcmdparser.add_dcmd_option(&_jdp_address);
    _dcmdparser.add_dcmd_option(&_jdp_source_addr);
    _dcmdparser.add_dcmd_option(&_jdp_ttl);
    _dcmdparser.add_dcmd_option(&_jdp_pause);
    _dcmdparser.add_dcmd_option(&_jdp_name);
}

void JMXStartRemoteDCmd::execute(DCmdSource source, TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the jdk.internal.agent.Agent class
    // invoke startRemoteManagementAgent(string) method to start
    // the remote management server.
    // throw java.lang.NoSuchMethodError if the method doesn't exist

    loadAgentModule(CHECK);
    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_agent_Agent(), loader, true, CHECK);

    JavaValue result(T_VOID);

    // Pass all command line arguments to java as key=value,...
    // All checks are done on java side

    int len = 0;
    stringStream options;
    char comma[2] = {0,0};

    // Leave default values on Agent.class side and pass only
    // arguments explicitly set by user. All arguments passed
    // to jcmd override properties with the same name set by
    // command line with -D or by managmenent.properties
    // file.
#define PUT_OPTION(a) \
    do { \
        if ( (a).is_set() ){ \
            if ( *((a).type()) == 'I' ) { \
                options.print("%scom.sun.management.%s=" JLONG_FORMAT, comma, (a).name(), (jlong)((a).value())); \
            } else { \
                options.print("%scom.sun.management.%s=%s", comma, (a).name(), (char*)((a).value())); \
            } \
            comma[0] = ','; \
        }\
    } while(0);


    PUT_OPTION(_config_file);
    PUT_OPTION(_jmxremote_host);
    PUT_OPTION(_jmxremote_port);
    PUT_OPTION(_jmxremote_rmi_port);
    PUT_OPTION(_jmxremote_ssl);
    PUT_OPTION(_jmxremote_registry_ssl);
    PUT_OPTION(_jmxremote_authenticate);
    PUT_OPTION(_jmxremote_password_file);
    PUT_OPTION(_jmxremote_access_file);
    PUT_OPTION(_jmxremote_login_config);
    PUT_OPTION(_jmxremote_ssl_enabled_cipher_suites);
    PUT_OPTION(_jmxremote_ssl_enabled_protocols);
    PUT_OPTION(_jmxremote_ssl_need_client_auth);
    PUT_OPTION(_jmxremote_ssl_config_file);
    PUT_OPTION(_jmxremote_autodiscovery);
    PUT_OPTION(_jdp_port);
    PUT_OPTION(_jdp_address);
    PUT_OPTION(_jdp_source_addr);
    PUT_OPTION(_jdp_ttl);
    PUT_OPTION(_jdp_pause);
    PUT_OPTION(_jdp_name);

#undef PUT_OPTION

    Handle str = java_lang_String::create_from_str(options.as_string(), CHECK);
    JavaCalls::call_static(&result, k, vmSymbols::startRemoteAgent_name(), vmSymbols::string_void_signature(), str, CHECK);
}

JMXStartLocalDCmd::JMXStartLocalDCmd(outputStream *output, bool heap_allocated) :
  DCmd(output, heap_allocated) {
  // do nothing
}

void JMXStartLocalDCmd::execute(DCmdSource source, TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the jdk.internal.agent.Agent class
    // invoke startLocalManagementAgent(void) method to start
    // the local management server
    // throw java.lang.NoSuchMethodError if method doesn't exist

    loadAgentModule(CHECK);
    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_agent_Agent(), loader, true, CHECK);

    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, k, vmSymbols::startLocalAgent_name(), vmSymbols::void_method_signature(), CHECK);
}

void JMXStopRemoteDCmd::execute(DCmdSource source, TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the jdk.internal.agent.Agent class
    // invoke stopRemoteManagementAgent method to stop the
    // management server
    // throw java.lang.NoSuchMethodError if method doesn't exist

    loadAgentModule(CHECK);
    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_agent_Agent(), loader, true, CHECK);

    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, k, vmSymbols::stopRemoteAgent_name(), vmSymbols::void_method_signature(), CHECK);
}

JMXStatusDCmd::JMXStatusDCmd(outputStream *output, bool heap_allocated) :
  DCmd(output, heap_allocated) {
  // do nothing
}

void JMXStatusDCmd::execute(DCmdSource source, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  // Load and initialize the jdk.internal.agent.Agent class
  // invoke getManagementAgentStatus() method to generate the status info
  // throw java.lang.NoSuchMethodError if method doesn't exist

  loadAgentModule(CHECK);
  Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_agent_Agent(), loader, true, CHECK);

  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result, k, vmSymbols::getAgentStatus_name(), vmSymbols::void_string_signature(), CHECK);

  jvalue* jv = (jvalue*) result.get_value_addr();
  oop str = cast_to_oop(jv->l);
  if (str != nullptr) {
      char* out = java_lang_String::as_utf8_string(str);
      if (out) {
          // Avoid using print_cr() because length maybe longer than O_BUFLEN
          output()->print_raw_cr(out);
          return;
      }
  }
  output()->print_cr("Error obtaining management agent status");
}

VMDynamicLibrariesDCmd::VMDynamicLibrariesDCmd(outputStream *output, bool heap_allocated) :
  DCmd(output, heap_allocated) {
  // do nothing
}

void VMDynamicLibrariesDCmd::execute(DCmdSource source, TRAPS) {
  os::print_dll_info(output());
  output()->cr();
}

void CompileQueueDCmd::execute(DCmdSource source, TRAPS) {
  VM_PrintCompileQueue printCompileQueueOp(output());
  VMThread::execute(&printCompileQueueOp);
}

void CodeListDCmd::execute(DCmdSource source, TRAPS) {
  CodeCache::print_codelist(output());
}

void CodeCacheDCmd::execute(DCmdSource source, TRAPS) {
  CodeCache::print_layout(output());
}

#ifdef LINUX
PerfMapDCmd::PerfMapDCmd(outputStream* output, bool heap) :
             DCmdWithParser(output, heap),
  _filename("filename", "Name of the map file", "FILE", false, DEFAULT_PERFMAP_FILENAME)
{
  _dcmdparser.add_dcmd_argument(&_filename);
}

void PerfMapDCmd::execute(DCmdSource source, TRAPS) {
  CodeCache::write_perf_map(_filename.value(), output());
}
#endif // LINUX

//---<  BEGIN  >--- CodeHeap State Analytics.
CodeHeapAnalyticsDCmd::CodeHeapAnalyticsDCmd(outputStream* output, bool heap) :
                                             DCmdWithParser(output, heap),
  _function("function", "Function to be performed (aggregate, UsedSpace, FreeSpace, MethodCount, MethodSpace, MethodAge, MethodNames, discard", "STRING", false, "all"),
  _granularity("granularity", "Detail level - smaller value -> more detail", "INT", false, "4096") {
  _dcmdparser.add_dcmd_argument(&_function);
  _dcmdparser.add_dcmd_argument(&_granularity);
}

void CodeHeapAnalyticsDCmd::execute(DCmdSource source, TRAPS) {
  jlong granularity = _granularity.value();
  if (granularity < 1) {
    Exceptions::fthrow(THREAD_AND_LOCATION, vmSymbols::java_lang_IllegalArgumentException(),
                       "Invalid granularity value " JLONG_FORMAT  ". Should be positive.\n", granularity);
    return;
  }

  CompileBroker::print_heapinfo(output(), _function.value(), granularity);
}
//---<  END  >--- CodeHeap State Analytics.

EventLogDCmd::EventLogDCmd(outputStream* output, bool heap) :
  DCmdWithParser(output, heap),
  _log("log", "Name of log to be printed. If omitted, all logs are printed.", "STRING", false, nullptr),
  _max("max", "Maximum number of events to be printed (newest first). If omitted or zero, all events are printed.", "INT", false, "0")
{
  _dcmdparser.add_dcmd_option(&_log);
  _dcmdparser.add_dcmd_option(&_max);
}

void EventLogDCmd::execute(DCmdSource source, TRAPS) {
  int max = (int)_max.value();
  if (max < 0) {
    output()->print_cr("Invalid max option: \"%d\".", max);
    return;
  }
  const char* log_name = _log.value();
  if (log_name != nullptr) {
    Events::print_one(output(), log_name, max);
  } else {
    Events::print_all(output(), max);
  }
}

void CompilerDirectivesPrintDCmd::execute(DCmdSource source, TRAPS) {
  DirectivesStack::print(output());
}

CompilerDirectivesAddDCmd::CompilerDirectivesAddDCmd(outputStream* output, bool heap) :
                           DCmdWithParser(output, heap),
  _filename("filename","Name of the directives file", "STRING",true) {
  _dcmdparser.add_dcmd_argument(&_filename);
}

void CompilerDirectivesAddDCmd::execute(DCmdSource source, TRAPS) {
  DirectivesParser::parse_from_file(_filename.value(), output(), true);
}

void CompilerDirectivesRemoveDCmd::execute(DCmdSource source, TRAPS) {
  DirectivesStack::pop(1);
}

void CompilerDirectivesClearDCmd::execute(DCmdSource source, TRAPS) {
  DirectivesStack::clear();
}
#if INCLUDE_SERVICES
ClassHierarchyDCmd::ClassHierarchyDCmd(outputStream* output, bool heap) :
                                       DCmdWithParser(output, heap),
  _print_interfaces("-i", "Inherited interfaces should be printed.", "BOOLEAN", false, "false"),
  _print_subclasses("-s", "If a classname is specified, print its subclasses "
                    "in addition to its superclasses. Without this option only the "
                    "superclasses will be printed.", "BOOLEAN", false, "false"),
  _classname("classname", "Name of class whose hierarchy should be printed. "
             "If not specified, all class hierarchies are printed.",
             "STRING", false) {
  _dcmdparser.add_dcmd_option(&_print_interfaces);
  _dcmdparser.add_dcmd_option(&_print_subclasses);
  _dcmdparser.add_dcmd_argument(&_classname);
}

void ClassHierarchyDCmd::execute(DCmdSource source, TRAPS) {
  VM_PrintClassHierarchy printClassHierarchyOp(output(), _print_interfaces.value(),
                                               _print_subclasses.value(), _classname.value());
  VMThread::execute(&printClassHierarchyOp);
}
#endif

ClassesDCmd::ClassesDCmd(outputStream* output, bool heap) :
                                     DCmdWithParser(output, heap),
  _verbose("-verbose",
           "Dump the detailed content of a Java class. "
           "Some classes are annotated with flags: "
           "F = has, or inherits, a non-empty finalize method, "
           "f = has final method, "
           "W = methods rewritten, "
           "C = marked with @Contended annotation, "
           "R = has been redefined, "
           "S = is shared class",
           "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_verbose);
}

class VM_PrintClasses : public VM_Operation {
private:
  outputStream* _out;
  bool _verbose;
public:
  VM_PrintClasses(outputStream* out, bool verbose) : _out(out), _verbose(verbose) {}

  virtual VMOp_Type type() const { return VMOp_PrintClasses; }

  virtual void doit() {
    PrintClassClosure closure(_out, _verbose);
    ClassLoaderDataGraph::classes_do(&closure);
  }
};

void ClassesDCmd::execute(DCmdSource source, TRAPS) {
  VM_PrintClasses vmop(output(), _verbose.value());
  VMThread::execute(&vmop);
}

#if INCLUDE_CDS
#define DEFAULT_CDS_ARCHIVE_FILENAME "java_pid%p_<subcmd>.jsa"

DumpSharedArchiveDCmd::DumpSharedArchiveDCmd(outputStream* output, bool heap) :
                                     DCmdWithParser(output, heap),
  _suboption("subcmd", "static_dump | dynamic_dump", "STRING", true),
  _filename("filename", "Name of shared archive to be dumped", "FILE", false,
            DEFAULT_CDS_ARCHIVE_FILENAME)
{
  _dcmdparser.add_dcmd_argument(&_suboption);
  _dcmdparser.add_dcmd_argument(&_filename);
}

void DumpSharedArchiveDCmd::execute(DCmdSource source, TRAPS) {
  jboolean is_static;
  const char* scmd = _suboption.value();

  // The check for _filename.is_set() is because we don't want to use
  // DEFAULT_CDS_ARCHIVE_FILENAME, since it is meant as a description
  // of the default, not the actual default.
  const char* file = _filename.is_set() ? _filename.value() : nullptr;

  if (strcmp(scmd, "static_dump") == 0) {
    is_static = JNI_TRUE;
    output()->print("Static dump: ");
  } else if (strcmp(scmd, "dynamic_dump") == 0) {
    is_static = JNI_FALSE;
    output()->print("Dynamic dump: ");
    if (!CDSConfig::is_using_archive()) {
      output()->print_cr("Dynamic dump is unsupported when base CDS archive is not loaded");
      return;
    }
    if (!RecordDynamicDumpInfo) {
      output()->print_cr("Dump dynamic should run with -XX:+RecordDynamicDumpInfo");
      return;
    }
  } else {
    output()->print_cr("Invalid command for VM.cds, valid input is static_dump or dynamic_dump");
    return;
  }

  // call CDS.dumpSharedArchive
  Handle fileh;
  if (file != nullptr) {
    fileh = java_lang_String::create_from_str(file, CHECK);
  }
  Symbol* cds_name  = vmSymbols::jdk_internal_misc_CDS();
  Klass*  cds_klass = SystemDictionary::resolve_or_fail(cds_name, true /*throw error*/,  CHECK);
  JavaValue result(T_OBJECT);
  JavaCallArguments args;
  args.push_int(is_static);
  args.push_oop(fileh);
  JavaCalls::call_static(&result,
                         cds_klass,
                         vmSymbols::dumpSharedArchive(),
                         vmSymbols::dumpSharedArchive_signature(),
                         &args, CHECK);
  if (!HAS_PENDING_EXCEPTION) {
    assert(result.get_type() == T_OBJECT, "Sanity check");
    // result contains the archive name
    char* archive_name = java_lang_String::as_utf8_string(result.get_oop());
    output()->print_cr("%s", archive_name);
  }
}
#endif // INCLUDE_CDS

ThreadDumpToFileDCmd::ThreadDumpToFileDCmd(outputStream* output, bool heap) :
                                           DCmdWithParser(output, heap),
  _overwrite("-overwrite", "May overwrite existing file", "BOOLEAN", false, "false"),
  _format("-format", "Output format (\"plain\" or \"json\")", "STRING", false, "plain"),
  _filepath("filepath", "The file path to the output file", "FILE", true) {
  _dcmdparser.add_dcmd_option(&_overwrite);
  _dcmdparser.add_dcmd_option(&_format);
  _dcmdparser.add_dcmd_argument(&_filepath);
}

void ThreadDumpToFileDCmd::execute(DCmdSource source, TRAPS) {
  bool json = (_format.value() != nullptr) && (strcmp(_format.value(), "json") == 0);
  char* path = _filepath.value();
  bool overwrite = _overwrite.value();
  Symbol* name = (json) ? vmSymbols::dumpThreadsToJson_name() : vmSymbols::dumpThreads_name();
  dumpToFile(name, vmSymbols::string_bool_byte_array_signature(), path, overwrite, CHECK);
}

void ThreadDumpToFileDCmd::dumpToFile(Symbol* name, Symbol* signature, const char* path, bool overwrite, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  Handle h_path = java_lang_String::create_from_str(path, CHECK);

  Symbol* sym = vmSymbols::jdk_internal_vm_ThreadDumper();
  Klass* k = SystemDictionary::resolve_or_fail(sym, true, CHECK);

  // invoke the ThreadDump method to dump to file
  JavaValue result(T_OBJECT);
  JavaCallArguments args;
  args.push_oop(h_path);
  args.push_int(overwrite ? JNI_TRUE : JNI_FALSE);
  JavaCalls::call_static(&result,
                         k,
                         name,
                         signature,
                         &args,
                         THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output());
    output()->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // check that result is byte array
  oop res = cast_to_oop(result.get_jobject());
  assert(res->is_typeArray(), "just checking");
  assert(TypeArrayKlass::cast(res->klass())->element_type() == T_BYTE, "just checking");

  // copy the bytes to the output stream
  typeArrayOop ba = typeArrayOop(res);
  jbyte* addr = typeArrayOop(res)->byte_at_addr(0);
  output()->print_raw((const char*)addr, ba->length());
}

// Calls a static no-arg method on jdk.internal.vm.JcmdVThreadCommands that returns a byte[] with
// the output. If the method completes successfully then the bytes are copied to the output stream.
// If the method fails then the exception is printed to the output stream.
static void execute_vthread_command(Symbol* method_name, outputStream* output, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::jdk_internal_vm_JcmdVThreadCommands(), true, CHECK);

  JavaValue result(T_OBJECT);
  JavaCallArguments args;
  JavaCalls::call_static(&result,
                         k,
                         method_name,
                         vmSymbols::void_byte_array_signature(),
                         &args,
                         THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output);
    output->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // copy the bytes to the output stream
  oop res = cast_to_oop(result.get_jobject());
  typeArrayOop ba = typeArrayOop(res);
  jbyte* addr = typeArrayOop(res)->byte_at_addr(0);
  output->print_raw((const char*)addr, ba->length());
}

void VThreadSchedulerDCmd::execute(DCmdSource source, TRAPS) {
  execute_vthread_command(vmSymbols::printScheduler_name(), output(), CHECK);
}

void VThreadPollersDCmd::execute(DCmdSource source, TRAPS) {
  execute_vthread_command(vmSymbols::printPollers_name(), output(), CHECK);
}

CompilationMemoryStatisticDCmd::CompilationMemoryStatisticDCmd(outputStream* output, bool heap) :
    DCmdWithParser(output, heap),
  _verbose("verbose", "Print detailed information", "BOOLEAN", false, "false"),
  _legend("legend", "Table mode: print legend", "BOOLEAN", false, "false"),
  _minsize("minsize", "Minimum memory size", "MEMORY SIZE", false, "0") {
  _dcmdparser.add_dcmd_option(&_verbose);
  _dcmdparser.add_dcmd_option(&_minsize);
  _dcmdparser.add_dcmd_option(&_legend);
}

void CompilationMemoryStatisticDCmd::execute(DCmdSource source, TRAPS) {
  const size_t minsize = _minsize.has_value() ? _minsize.value()._size : 0;
  CompilationMemoryStatistic::print_jcmd_report(output(), _verbose.value(), _legend.value(), minsize);
}

#if defined(LINUX) || defined(_WIN64) || defined(__APPLE__)

SystemMapDCmd::SystemMapDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}

void SystemMapDCmd::execute(DCmdSource source, TRAPS) {
  MemMapPrinter::print_all_mappings(output());
}

static constexpr char default_filename[] = "vm_memory_map_%p.txt";

SystemDumpMapDCmd::SystemDumpMapDCmd(outputStream* output, bool heap) :
  DCmdWithParser(output, heap),
  _filename("-F", "file path", "FILE", false, default_filename) {
  _dcmdparser.add_dcmd_option(&_filename);
}

void SystemDumpMapDCmd::execute(DCmdSource source, TRAPS) {
  const char* name = _filename.value();
  if (name == nullptr || name[0] == 0) {
    output()->print_cr("filename is empty or not specified.  No file written");
    return;
  }
  fileStream fs(name);
  if (fs.is_open()) {
    if (!MemTracker::enabled()) {
      output()->print_cr("(NMT is disabled, will not annotate mappings).");
    }
    MemMapPrinter::print_all_mappings(&fs);
    // For the readers convenience, resolve path name.
    char tmp[JVM_MAXPATHLEN];
    const char* absname = os::realpath(name, tmp, sizeof(tmp));
    name = absname != nullptr ? absname : name;
    output()->print_cr("Memory map dumped to \"%s\".", name);
  } else {
    output()->print_cr("Failed to open \"%s\" for writing (%s).", name, os::strerror(errno));
  }
}

#endif // LINUX
