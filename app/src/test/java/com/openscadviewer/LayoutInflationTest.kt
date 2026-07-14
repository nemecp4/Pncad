package com.openscadviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Integration tests for layout inflation structural verification.
 *
 * Since Robolectric is not available, these tests parse the layout XML files directly
 * to verify structural properties of the tablet and phone layouts:
 * - paneDivider presence/absence
 * - pane orientation (horizontal for landscape, vertical for portrait)
 * - button bar presence and full-width spanning
 *
 * Requirements: 1.1, 1.2, 1.3, 7.1, 7.2, 5.1
 */
class LayoutInflationTest {

    private val xmlFactory = DocumentBuilderFactory.newInstance()

    private fun parseLayout(resourcePath: String): Document {
        val inputStream = javaClass.classLoader!!.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Layout resource not found: $resourcePath")
        return xmlFactory.newDocumentBuilder().parse(inputStream)
    }

    /**
     * Recursively finds all elements with a given android:id attribute value.
     */
    private fun findElementsById(element: Element, idValue: String): List<Element> {
        val results = mutableListOf<Element>()
        val androidId = element.getAttribute("android:id")
        if (androidId == "@+id/$idValue" || androidId == "@id/$idValue") {
            results.add(element)
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                results.addAll(findElementsById(child, idValue))
            }
        }
        return results
    }

    /**
     * Finds the first element with the given android:id.
     */
    private fun findElementById(doc: Document, idValue: String): Element? {
        return findElementsById(doc.documentElement, idValue).firstOrNull()
    }

    /**
     * Finds the LinearLayout that directly contains both the code editor pane and preview pane,
     * i.e., the panes container whose orientation defines the arrangement.
     */
    private fun findPanesContainer(doc: Document): Element? {
        // The panes container is the LinearLayout that has paneDivider as a direct child
        val paneDivider = findElementById(doc, "paneDivider") ?: return null
        val parent = paneDivider.parentNode
        if (parent is Element && parent.tagName == "LinearLayout") {
            return parent
        }
        return null
    }

    @Nested
    @DisplayName("Tablet Layout Detection - paneDivider presence (Requirements 1.1, 1.2, 1.3)")
    inner class PaneDividerPresenceTests {

        @Test
        @DisplayName("sw600dp landscape layout contains paneDivider (tablet detected)")
        fun tabletLandscapeLayout_containsPaneDivider() {
            val doc = parseLayout("layout-sw600dp-land/activity_main.xml")
            val paneDivider = findElementById(doc, "paneDivider")
            assertNotNull(paneDivider, "Tablet landscape layout must contain a view with id 'paneDivider'")
        }

        @Test
        @DisplayName("sw600dp portrait layout contains paneDivider (tablet detected)")
        fun tabletPortraitLayout_containsPaneDivider() {
            val doc = parseLayout("layout-sw600dp-port/activity_main.xml")
            val paneDivider = findElementById(doc, "paneDivider")
            assertNotNull(paneDivider, "Tablet portrait layout must contain a view with id 'paneDivider'")
        }

        @Test
        @DisplayName("Default phone layout does NOT contain paneDivider")
        fun phoneLayout_doesNotContainPaneDivider() {
            val doc = parseLayout("layout/activity_main.xml")
            val paneDivider = findElementById(doc, "paneDivider")
            assertNull(paneDivider, "Phone layout must NOT contain a view with id 'paneDivider'")
        }
    }

    @Nested
    @DisplayName("Tablet Landscape Arrangement (Requirement 7.1)")
    inner class TabletLandscapeArrangementTests {

        @Test
        @DisplayName("Tablet landscape panes container has horizontal orientation")
        fun tabletLandscape_panesContainerIsHorizontal() {
            val doc = parseLayout("layout-sw600dp-land/activity_main.xml")
            val panesContainer = findPanesContainer(doc)
            assertNotNull(panesContainer, "Panes container LinearLayout must exist")
            val orientation = panesContainer!!.getAttribute("android:orientation")
            assertEquals(
                "horizontal",
                orientation,
                "Tablet landscape panes container must have android:orientation='horizontal'"
            )
        }
    }

    @Nested
    @DisplayName("Tablet Portrait Arrangement (Requirement 7.2)")
    inner class TabletPortraitArrangementTests {

        @Test
        @DisplayName("Tablet portrait panes container has vertical orientation")
        fun tabletPortrait_panesContainerIsVertical() {
            val doc = parseLayout("layout-sw600dp-port/activity_main.xml")
            val panesContainer = findPanesContainer(doc)
            assertNotNull(panesContainer, "Panes container LinearLayout must exist")
            val orientation = panesContainer!!.getAttribute("android:orientation")
            assertEquals(
                "vertical",
                orientation,
                "Tablet portrait panes container must have android:orientation='vertical'"
            )
        }
    }

    @Nested
    @DisplayName("Button Bar in Tablet Mode (Requirement 5.1)")
    inner class ButtonBarTests {

        @Test
        @DisplayName("Tablet landscape layout has buttonBar with horizontal orientation and match_parent width")
        fun tabletLandscape_buttonBarPresent_andSpansFullWidth() {
            val doc = parseLayout("layout-sw600dp-land/activity_main.xml")
            val buttonBar = findElementById(doc, "buttonBar")
            assertNotNull(buttonBar, "Tablet landscape layout must contain buttonBar")
            assertEquals(
                "horizontal",
                buttonBar!!.getAttribute("android:orientation"),
                "Button bar must have horizontal orientation"
            )
            assertEquals(
                "match_parent",
                buttonBar.getAttribute("android:layout_width"),
                "Button bar must span full width (match_parent)"
            )
        }

        @Test
        @DisplayName("Tablet portrait layout has buttonBar with horizontal orientation and match_parent width")
        fun tabletPortrait_buttonBarPresent_andSpansFullWidth() {
            val doc = parseLayout("layout-sw600dp-port/activity_main.xml")
            val buttonBar = findElementById(doc, "buttonBar")
            assertNotNull(buttonBar, "Tablet portrait layout must contain buttonBar")
            assertEquals(
                "horizontal",
                buttonBar!!.getAttribute("android:orientation"),
                "Button bar must have horizontal orientation"
            )
            assertEquals(
                "match_parent",
                buttonBar.getAttribute("android:layout_width"),
                "Button bar must span full width (match_parent)"
            )
        }
    }
}
