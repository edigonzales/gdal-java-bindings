package ch.so.agi.gdal.ffm.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class CArgv {
    private CArgv() {
    }

    static MemorySegment toCStringArray(String[] args, Arena arena) {
        String[] safeArgs = args == null ? new String[0] : args;
        MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, safeArgs.length + 1L);
        for (int i = 0; i < safeArgs.length; i++) {
            String arg = safeArgs[i];
            if (arg == null) {
                throw new IllegalArgumentException("args[" + i + "] must not be null");
            }
            MemorySegment value = arena.allocateFrom(arg);
            argv.setAtIndex(ValueLayout.ADDRESS, i, value);
        }
        argv.setAtIndex(ValueLayout.ADDRESS, safeArgs.length, MemorySegment.NULL);
        return argv;
    }
}
