package com.wynime.app.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions
import java.io.File

interface ExecutableDirectoryDetector {

    fun getExecutableDirectory(): File

    companion object {
        val INSTANCE: ExecutableDirectoryDetector by lazy {
            WindowsExecutableDirectoryDetector
        }
    }
}

object WindowsExecutableDirectoryDetector : ExecutableDirectoryDetector {
    @Suppress("FunctionName")
    interface MyKernel32 : Kernel32 {

        fun GetModuleFileNameW(
            hModule: WinDef.HMODULE?,
            lpFilename: CharArray,
            nSize: Int
        ): Int

        companion object {
            val INSTANCE: MyKernel32 by lazy {
                Native.load(
                    "kernel32",
                    MyKernel32::class.java,
                    W32APIOptions.DEFAULT_OPTIONS,
                )
            }
        }
    }

    override fun getExecutableDirectory(): File {
        val buffer = CharArray(1024)
        val length = MyKernel32.INSTANCE.GetModuleFileNameW(null, buffer, buffer.size)
        val fullPath = String(buffer, 0, length)
        return File(fullPath).parentFile
    }
}