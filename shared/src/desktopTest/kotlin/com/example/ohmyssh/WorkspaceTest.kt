package com.example.ohmyssh

import com.example.ohmyssh.session.PaneRef
import com.example.ohmyssh.session.Workspace
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceTest {
    private val shellA = PaneRef.Shell("a")
    private val filesA = PaneRef.Files("a")
    private val shellB = PaneRef.Shell("b")

    private fun refsOnScreen() = Workspace.windows.map { it.ref }

    @BeforeTest
    fun clean() = Workspace.reset()

    @AfterTest
    fun tidy() = Workspace.reset()

    @Test
    fun splittingAddsASecondWindowToThisGroup() {
        Workspace.openGroup(shellA)
        assertFalse(Workspace.isSplit)
        assertEquals(1, Workspace.groups.size)

        Workspace.split()

        assertEquals(1, Workspace.groups.size)
        assertEquals(listOf(shellA, PaneRef.Picker), refsOnScreen())
        assertTrue(Workspace.isSplit)
        assertEquals(PaneRef.Picker, Workspace.focusedWindow?.ref)
    }

    @Test
    fun whatIsPickedTakesOverThatWindowOnly() {
        Workspace.openGroup(shellA)
        Workspace.split()
        val picker = Workspace.focusedWindow!!

        Workspace.resolve(picker.id, shellB)

        assertEquals(listOf(shellA, shellB), refsOnScreen())
        assertEquals(picker.id, Workspace.focusedWindow?.id)
    }

    @Test
    fun reachingAnotherGroupSwapsBothWindows() {
        Workspace.openGroup(shellA)
        Workspace.split()
        Workspace.resolve(Workspace.focusedWindow!!.id, filesA)
        val first = Workspace.activeGroup!!

        val second = Workspace.openGroup(shellB)
        assertEquals(listOf(shellB), refsOnScreen())

        Workspace.focusWindow(first.windows.first().id)
        assertEquals(first.id, Workspace.activeGroupId)
        assertEquals(listOf(shellA, filesA), refsOnScreen())

        Workspace.activate(second.id)
        assertEquals(listOf(shellB), refsOnScreen())
    }

    @Test
    fun focusingAWindowOfTheGroupOnScreenLeavesTheOtherAlone() {
        Workspace.openGroup(shellA)
        Workspace.split()
        Workspace.resolve(Workspace.focusedWindow!!.id, filesA)

        val shellWindow = Workspace.windows.first()
        Workspace.focusWindow(shellWindow.id)

        assertEquals(listOf(shellA, filesA), refsOnScreen())
        assertEquals(shellWindow.id, Workspace.focusedWindow?.id)
    }

    @Test
    fun openingWhatIsAlreadyInThisGroupBringsItForward() {
        Workspace.openGroup(shellA)
        Workspace.addWindow(filesA)
        val files = Workspace.focusedWindow!!

        Workspace.focusWindow(Workspace.windows.first().id)
        Workspace.addWindow(filesA)

        assertEquals(2, Workspace.windows.size)
        assertEquals(files.id, Workspace.focusedWindow?.id)
    }

    @Test
    fun aThirdWindowRepointsTheFocusedOneRatherThanCrowdingTheGroup() {
        Workspace.openGroup(shellA)
        Workspace.addWindow(filesA)
        val focusedId = Workspace.focusedWindow!!.id

        Workspace.addWindow(shellB)

        assertEquals(listOf(shellA, shellB), refsOnScreen())
        assertEquals(focusedId, Workspace.focusedWindow?.id)
    }

    @Test
    fun unsplitKeepsTheFocusedWindow() {
        Workspace.openGroup(shellA)
        Workspace.addWindow(filesA)
        Workspace.focusWindow(Workspace.windows.first().id)

        Workspace.unsplit()

        assertEquals(listOf(shellA), refsOnScreen())
        assertFalse(Workspace.isSplit)
    }

    @Test
    fun anEmptiedGroupGoesAndTheLastOneTakesTheScreen() {
        val first = Workspace.openGroup(shellA)
        Workspace.openGroup(shellB)

        Workspace.closeWindow(Workspace.focusedWindow!!.id)

        assertEquals(listOf(first.id), Workspace.groups.map { it.id })
        assertEquals(listOf(shellA), refsOnScreen())

        Workspace.closeWindow(Workspace.focusedWindow!!.id)
        assertTrue(Workspace.groups.isEmpty())
        assertNull(Workspace.focusedWindow)
    }

    @Test
    fun aClosedSessionTakesEveryWindowThatWasShowingIt() {
        Workspace.openGroup(shellA)
        Workspace.addWindow(filesA)
        Workspace.openGroup(shellB)

        Workspace.dropSession("a")

        assertEquals(1, Workspace.groups.size)
        assertEquals(listOf(shellB), refsOnScreen())
    }

    @Test
    fun revealingASessionGoesToWhereItAlreadyIsInsteadOfCloningIt() {
        val first = Workspace.openGroup(shellA)
        Workspace.openGroup(shellB)

        val found = Workspace.reveal(shellA)

        assertEquals(first.id, found.id)
        assertEquals(2, Workspace.groups.size)
        assertEquals(shellA, Workspace.focusedWindow?.ref)
    }

    @Test
    fun twoBrowsersOnThisDeviceAreAllowedSideBySide() {
        Workspace.openGroup(PaneRef.Local)
        Workspace.addWindow(PaneRef.Local)

        assertEquals(listOf(PaneRef.Local, PaneRef.Local), refsOnScreen())
    }
}
