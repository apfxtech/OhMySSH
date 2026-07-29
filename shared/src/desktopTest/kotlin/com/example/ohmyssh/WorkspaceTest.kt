package com.example.ohmyssh

import com.example.ohmyssh.session.PaneKind
import com.example.ohmyssh.session.Workspace
import com.example.ohmyssh.session.paneKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceTest {
    private val shellA = paneKey("a", PaneKind.SHELL)
    private val filesA = paneKey("a", PaneKind.FILES)
    private val shellB = paneKey("b", PaneKind.SHELL)

    @BeforeTest
    fun clean() = Workspace.reset()

    @AfterTest
    fun tidy() = Workspace.reset()

    @Test
    fun tappingATabReplacesTheFocusedPane() {
        Workspace.show(shellA)
        assertEquals(listOf(shellA), Workspace.slots.toList())

        Workspace.show(shellB)
        assertEquals(listOf(shellB), Workspace.slots.toList())
        assertFalse(Workspace.isSplit)
    }

    @Test
    fun opensBesideUpToTwoPanes() {
        Workspace.show(shellA)
        Workspace.showBeside(filesA)
        assertEquals(listOf(shellA, filesA), Workspace.slots.toList())
        assertTrue(Workspace.isSplit)
        assertEquals(filesA, Workspace.focusedKey)

        Workspace.showBeside(shellB)
        assertEquals(listOf(shellB, filesA), Workspace.slots.toList())
        assertEquals(shellB, Workspace.focusedKey)
    }

    @Test
    fun openingAPaneThatIsAlreadyShowingJustFocusesIt() {
        Workspace.show(shellA)
        Workspace.showBeside(filesA)
        Workspace.focus(0)

        Workspace.show(filesA)
        assertEquals(listOf(shellA, filesA), Workspace.slots.toList())
        assertEquals(filesA, Workspace.focusedKey)
    }

    @Test
    fun unsplitKeepsTheFocusedPane() {
        Workspace.show(shellA)
        Workspace.showBeside(filesA)
        Workspace.focus(0)
        Workspace.unsplit()

        assertEquals(listOf(shellA), Workspace.slots.toList())
        assertEquals(shellA, Workspace.focusedKey)
    }

    @Test
    fun closingAPaneDropsItsSlotAndKeepsSomethingUp() {
        Workspace.show(shellA)
        Workspace.showBeside(filesA)

        Workspace.hide(filesA)
        assertEquals(listOf(shellA), Workspace.slots.toList())
        assertEquals(shellA, Workspace.focusedKey)

        Workspace.reconcile(listOf(shellB))
        assertEquals(listOf(shellB), Workspace.slots.toList())

        Workspace.reconcile(emptyList())
        assertTrue(Workspace.slots.isEmpty())
        assertEquals(0, Workspace.focused)
    }

    @Test
    fun thePickedSystemTakesOverThePickersOwnSlot() {
        Workspace.show(shellA)
        Workspace.showBeside(Workspace.openPicker())
        val picker = Workspace.pickers.single()
        assertEquals(2, Workspace.slots.size)

        Workspace.resolvePicker(picker.id, shellB)

        assertEquals(listOf(shellA, shellB), Workspace.slots.toList())
        assertEquals(shellB, Workspace.focusedKey)
        assertTrue(Workspace.pickers.isEmpty())
    }

    @Test
    fun dismissingAPickerLeavesTheOtherPaneWhereItWas() {
        Workspace.show(shellA)
        val picker = Workspace.openPicker()
        Workspace.showBeside(picker)

        Workspace.closePicker(Workspace.pickers.single().id)

        assertEquals(listOf(shellA), Workspace.slots.toList())
        assertTrue(Workspace.pickers.isEmpty())
    }

    @Test
    fun localFilesReusesAPaneThatIsNotShowing() {
        val first = Workspace.requestLocal()
        assertEquals(1, Workspace.localPanes.size)

        assertEquals(first.id, Workspace.requestLocal().id)

        Workspace.show(paneKey(first.id, PaneKind.FILES))
        val second = Workspace.requestLocal()
        assertTrue(second.id != first.id)
        assertEquals(2, Workspace.localPanes.size)
    }
}
