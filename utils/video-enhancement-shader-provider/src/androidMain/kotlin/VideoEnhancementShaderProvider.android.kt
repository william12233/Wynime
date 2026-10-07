package com.wynime.utils.video.enhancement.shader.provider

import android.content.Context

actual object VideoEnhancementShaderProvider {
    private val shaderTemplatePlaceholder = Regex("""\{\{[A-Z_]+\}\}""")
    private const val RES_ROOT = "composeResources/com.wynime.utils.video.enhancement.shader.provider/files/shaders"

    fun getShaderSource(context: Context, shaderName: String): String =
        context.assets.open("$RES_ROOT/$shaderName").bufferedReader().use { it.readText() }

    fun renderShaderTemplate(template: String, vararg replacements: Pair<String, String>): String {
        var result = template
        replacements.forEach { (name, value) ->
            val placeholder = "{{$name}}"
            require(placeholder in result) { "Shader template does not contain $placeholder" }
            result = result.replace(placeholder, value)
        }
        require(!shaderTemplatePlaceholder.containsMatchIn(result)) {
            "Shader template contains an unresolved placeholder"
        }
        return result
    }
}
