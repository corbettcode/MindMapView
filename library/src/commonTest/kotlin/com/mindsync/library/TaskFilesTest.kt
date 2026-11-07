package com.mindsync.library

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskDate
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.HtmlOutline
import com.mindsync.library.io.Markdown
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.io.Opml
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskFilesTest {
    // The root with "p" below it, and "a", "b" and "c" below "p", deep enough to be list items in Markdown.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("p", getRootNode().id, "P")
        addNode("q", "p", "Q")
        addNode("r", "q", "R")
        listOf("a", "b", "c").forEach { addNode(it, "r", it.uppercase()) }
        setTask(getRootNode().id, NodeTask(priority = 9))
        setTask("a", NodeTask(priority = 1, progress = TaskProgress.HALF))
        setTask("b", NodeTask(progress = TaskProgress.DONE))
    }

    private fun Tree<Any>.taskNamed(text: String): NodeTask =
        taskOf(nodes.values.single { RichText.parse(it.description).text == text }.id)

    @Test
    fun tasksAreKeptInAMindMapViewFile() {
        val read = MindMapViewFile.read(MindMapViewFile.write(tree()))

        assertEquals(NodeTask(priority = 1, progress = TaskProgress.HALF), read.taskNamed("A"))
        assertEquals(NodeTask(progress = TaskProgress.DONE), read.taskNamed("B"))
        assertTrue(read.taskNamed("C").isEmpty)
        assertEquals(NodeTask(priority = 9), read.taskOf(read.getRootNode().id))
    }

    @Test
    fun taskThisLibraryHasNotIsLeftOutOfAMindMapViewFile() {
        val content = """
            <mindmapview version="1">
              <node id="r" text="root">
                <node id="a" text="A" priority="12" progress="halfway"/>
              </node>
            </mindmapview>
        """.trimIndent()

        assertTrue(MindMapViewFile.read(content).taskNamed("A").isEmpty)
    }

    @Test
    fun tasksAreFreeplanesIcons() {
        val content = FreeMind.write(tree())

        assertTrue("""<icon BUILTIN="full-1"/>""" in content)
        assertTrue("""<icon BUILTIN="50%"/>""" in content)
        assertTrue("""<icon BUILTIN="100%"/>""" in content)
        assertTrue("""<icon BUILTIN="button_ok"/>""" in content)
        val read = FreeMind.read(content)
        assertEquals(NodeTask(priority = 1, progress = TaskProgress.HALF), read.taskNamed("A"))
        assertEquals(NodeTask(progress = TaskProgress.DONE), read.taskNamed("B"))
        assertEquals(NodeTask(priority = 9), read.taskOf(read.getRootNode().id))
    }

    @Test
    fun iconsOfFreeMindAndFreeplaneAreRead() {
        val content = """
            <map version="freeplane 1.11.1">
              <node ID="r" TEXT="root">
                <node ID="a" TEXT="A"><icon BUILTIN="full-3"/><icon BUILTIN="button_ok"/></node>
                <node ID="b" TEXT="B"><icon BUILTIN="unchecked"/></node>
                <node ID="c" TEXT="C"><icon BUILTIN="checked"/></node>
                <node ID="d" TEXT="D"><icon BUILTIN="25%"/><icon BUILTIN="75%"/></node>
                <node ID="e" TEXT="E"><icon BUILTIN="full-0"/><icon BUILTIN="idea"/></node>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        assertEquals(NodeTask(priority = 3, progress = TaskProgress.DONE), read.taskNamed("A"))
        assertEquals(TaskProgress.NOT_STARTED, read.taskNamed("B").progress)
        assertEquals(TaskProgress.DONE, read.taskNamed("C").progress)
        // Of several progress icons, the furthest on is the progress.
        assertEquals(TaskProgress.THREE_QUARTERS, read.taskNamed("D").progress)
        assertTrue(read.taskNamed("E").isEmpty)
    }

    @Test
    fun tasksAreATaskListInMarkdown() {
        val content = Markdown.write(tree())

        // A task list has no steps between, so a task part done is not done.
        assertTrue("- [ ] A" in content, content)
        assertTrue("- [x] B" in content, content)
        assertTrue("- C" in content, content)
        val read = Markdown.read(content, title = "doc")
        assertEquals(TaskProgress.NOT_STARTED, read.taskNamed("A").progress)
        assertEquals(TaskProgress.DONE, read.taskNamed("B").progress)
        assertTrue(read.taskNamed("C").isEmpty)
    }

    @Test
    fun headingsHaveNoTaskInMarkdown() {
        val tree = tree()
        tree.setTask("p", NodeTask(progress = TaskProgress.DONE))

        val content = Markdown.write(tree)

        assertTrue("\n## P\n" in content, content)
    }

    @Test
    fun tasksAreAttributesInAnOpmlOutline() {
        val content = Opml.write(tree())

        assertTrue("""text="A" _priority="1" _progress="50"""" in content, content)
        assertTrue("""text="B" _progress="100"""" in content, content)
    }

    @Test
    fun tasksAreTextInAnHtmlOutline() {
        val content = HtmlOutline.write(tree())

        assertTrue("""[P1]</span> <span class="task">[50%]</span> <span class="node">A""" in content, content)
        assertTrue("""[Done]</span> <span class="node">B""" in content, content)
    }

    // "c" starts on the first and is due on the last of November, and "b" is only due.
    private fun datedTree(): Tree<Any> = tree().apply {
        setTask("c", NodeTask(start = TaskDate(2026, 11, 1), due = TaskDate(2026, 11, 30)))
        setTask("b", taskOf("b").copy(due = TaskDate(2027, 1, 15)))
    }

    @Test
    fun datesAreKeptInAMindMapViewFile() {
        val content = MindMapViewFile.write(datedTree())

        assertTrue("""start="2026-11-01" due="2026-11-30"""" in content, content)
        val read = MindMapViewFile.read(content)
        assertEquals(NodeTask(start = TaskDate(2026, 11, 1), due = TaskDate(2026, 11, 30)), read.taskNamed("C"))
        assertEquals(NodeTask(progress = TaskProgress.DONE, due = TaskDate(2027, 1, 15)), read.taskNamed("B"))
    }

    @Test
    fun datesThatAreNoDaysAreLeftOutOfAMindMapViewFile() {
        val content = """
            <mindmapview version="1">
              <node id="r" text="root">
                <node id="a" text="A" start="2026-02-30" due="soon" priority="2"/>
              </node>
            </mindmapview>
        """.trimIndent()

        assertEquals(NodeTask(priority = 2), MindMapViewFile.read(content).taskNamed("A"))
    }

    @Test
    fun datesAreAttributesInFreeMindKeptApartFromTheNodesOwn() {
        val tree = datedTree()
        tree.setAttributes("c", listOf(NodeAttribute("owner", "Sam")))

        val content = FreeMind.write(tree)

        assertTrue("""<attribute NAME="start" VALUE="2026-11-01"/>""" in content, content)
        assertTrue("""<attribute NAME="due" VALUE="2026-11-30"/>""" in content, content)
        val read = FreeMind.read(content)
        assertEquals(NodeTask(start = TaskDate(2026, 11, 1), due = TaskDate(2026, 11, 30)), read.taskNamed("C"))
        val c = read.nodes.values.single { RichText.parse(it.description).text == "C" }
        assertEquals(listOf(NodeAttribute("owner", "Sam")), read.attributesOf(c.id))
    }

    @Test
    fun attributeNamedDueThatIsNoDateStaysAnAttributeInFreeMind() {
        val content = """
            <map version="freeplane 1.11.1">
              <node ID="r" TEXT="root">
                <node ID="a" TEXT="A"><attribute NAME="due" VALUE="next week"/></node>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        assertTrue(read.taskNamed("A").isEmpty)
        assertEquals(listOf(NodeAttribute("due", "next week")), read.attributesOf("a"))
    }

    @Test
    fun datesAreAttributesInAnOpmlOutline() {
        val content = Opml.write(datedTree())

        assertTrue("""_start="2026-11-01" _due="2026-11-30"""" in content, content)
    }
}
