package com.openscadviewer.engine

import com.openscadviewer.parser.SceneNode
import net.jqwik.api.*
import net.jqwik.api.Combinators
import org.json.JSONObject
import kotlin.math.abs

/**
 * Property-based test for SceneSerializer text serialization round-trip.
 *
 * **Validates: Requirements 6.2**
 *
 * Property 9: Scene serialization round-trip preserves text parameters.
 * For any SceneNode.Text node, serializing to JSON and inspecting the result
 * shall yield a JSON object with type="text" and all parameter values matching
 * the original node's field values.
 */
class TextSerializerPropertyTest {

    companion object {
        private const val DOUBLE_TOLERANCE = 1e-9
    }

    @Property(tries = 200)
    @Tag("Feature_text-rendering")
    @Tag("Property_9_Scene_serialization_round-trip_preserves_text_parameters")
    fun `serialization round-trip preserves text parameters`(@ForAll("textNodes") textNode: SceneNode.Text) {
        val json = SceneSerializer.toJson(textNode)
        val obj = JSONObject(json)

        // Verify type field
        assert(obj.getString("type") == "text") {
            "Expected type='text', got '${obj.getString("type")}'"
        }

        // Verify text content
        assert(obj.getString("text") == textNode.text) {
            "text: expected='${textNode.text}', actual='${obj.getString("text")}'"
        }

        // Verify size
        assertDoubleEquals(textNode.size, obj.getDouble("size"), "size")

        // Verify font
        assert(obj.getString("font") == textNode.font) {
            "font: expected='${textNode.font}', actual='${obj.getString("font")}'"
        }

        // Verify halign
        assert(obj.getString("halign") == textNode.halign) {
            "halign: expected='${textNode.halign}', actual='${obj.getString("halign")}'"
        }

        // Verify valign
        assert(obj.getString("valign") == textNode.valign) {
            "valign: expected='${textNode.valign}', actual='${obj.getString("valign")}'"
        }

        // Verify spacing
        assertDoubleEquals(textNode.spacing, obj.getDouble("spacing"), "spacing")

        // Verify direction
        assert(obj.getString("direction") == textNode.direction) {
            "direction: expected='${textNode.direction}', actual='${obj.getString("direction")}'"
        }
    }

    @Provide
    fun textNodes(): Arbitrary<SceneNode.Text> {
        return Combinators.combine(
            textContentArbitrary(),
            sizeArbitrary(),
            fontArbitrary(),
            halignArbitrary(),
            valignArbitrary(),
            spacingArbitrary(),
            directionArbitrary()
        ).`as` { text, size, font, halign, valign, spacing, direction ->
            SceneNode.Text(
                text = text,
                size = size,
                font = font,
                halign = halign,
                valign = valign,
                spacing = spacing,
                direction = direction
            )
        }
    }

    private fun textContentArbitrary(): Arbitrary<String> {
        return Arbitraries.oneOf(
            // Empty string
            Arbitraries.just(""),
            // Single character
            Arbitraries.chars().alpha().map { it.toString() },
            // Short ASCII strings
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
            // Strings with spaces and punctuation
            Arbitraries.strings()
                .withCharRange('!', '~')  // printable ASCII
                .ofMinLength(1)
                .ofMaxLength(30),
            // Strings with unicode
            Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
        )
    }

    private fun sizeArbitrary(): Arbitrary<Double> {
        return Arbitraries.oneOf(
            // Typical sizes
            Arbitraries.doubles().between(0.1, 200.0),
            // Edge case: zero or negative
            Arbitraries.doubles().between(-10.0, 0.0),
            // Default value
            Arbitraries.just(10.0)
        )
    }

    private fun fontArbitrary(): Arbitrary<String> {
        return Arbitraries.of(
            "Liberation Sans",
            "Arial",
            "Times New Roman",
            "Courier New",
            "Helvetica",
            "DejaVu Sans",
            "Custom Font Name"
        )
    }

    private fun halignArbitrary(): Arbitrary<String> {
        return Arbitraries.of("left", "center", "right")
    }

    private fun valignArbitrary(): Arbitrary<String> {
        return Arbitraries.of("baseline", "bottom", "top", "center")
    }

    private fun spacingArbitrary(): Arbitrary<Double> {
        return Arbitraries.oneOf(
            // Typical spacing values
            Arbitraries.doubles().between(0.1, 5.0),
            // Default
            Arbitraries.just(1.0),
            // Very small
            Arbitraries.doubles().between(0.01, 0.1)
        )
    }

    private fun directionArbitrary(): Arbitrary<String> {
        return Arbitraries.of("ltr", "rtl")
    }

    private fun assertDoubleEquals(expected: Double, actual: Double, field: String) {
        assert(abs(expected - actual) < DOUBLE_TOLERANCE) {
            "$field: expected=$expected, actual=$actual, diff=${abs(expected - actual)}"
        }
    }
}
