package com.example.ohmyssh.platform

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Just enough of the Objective-C runtime to ask AppKit and CoreLocation the
 * questions no shell command answers. Every call goes through objc_msgSend, so
 * a wrong selector is a crash rather than a compile error — keep the callers
 * small and wrap them in runCatching.
 */
internal object ObjC {
    private val objc = NativeLibrary.getInstance("objc")
    private val getClass: Function = objc.getFunction("objc_getClass")
    private val registerSel: Function = objc.getFunction("sel_registerName")
    private val msgSend: Function = objc.getFunction("objc_msgSend")

    fun cls(name: String): Pointer? = getClass.invokePointer(arrayOf(name))

    fun sel(name: String): Pointer = registerSel.invokePointer(arrayOf(name))

    fun send(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? =
        msgSend.invokePointer(arrayOf(receiver, sel(selector), *args))

    /** For selectors returning BOOL or NSInteger, which come back in a register. */
    fun sendInt(receiver: Pointer?, selector: String, vararg args: Any?): Int =
        msgSend.invokeInt(arrayOf(receiver, sel(selector), *args))

    fun string(text: String): Pointer? = send(cls("NSString"), "stringWithUTF8String:", text)

    fun text(nsString: Pointer?): String? {
        if (nsString == null) return null
        return send(nsString, "UTF8String")?.getString(0)?.ifEmpty { null }
    }

    /** dlopen, so the framework's classes exist before the first objc_getClass. */
    fun loadFramework(path: String): Boolean =
        runCatching { NativeLibrary.getInstance(path) }.isSuccess
}
