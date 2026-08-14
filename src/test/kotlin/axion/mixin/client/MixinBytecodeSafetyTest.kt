package axion.mixin.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class MixinBytecodeSafetyTest {
    @Test
    fun `minecraft client mixin does not capture itself in runtime lambdas`() {
        val resourceName = "axion/mixin/client/MinecraftClientMixin.class"
        val stream = javaClass.classLoader.getResourceAsStream(resourceName)
        assertNotNull(stream, "Missing compiled client mixin: $resourceName")

        val bytecodeText = stream.use { bytes ->
            bytes.readBytes().toString(Charsets.ISO_8859_1)
        }
        val selfCapturingFunctionDescriptor =
            "(Laxion/mixin/client/MinecraftClientMixin;)Lkotlin/jvm/functions/Function0;"

        assertFalse(
            bytecodeText.contains(selfCapturingFunctionDescriptor),
            "Runtime lambdas must not capture a Mixin class; legacy Mixin loaders reject that class reference",
        )
    }
}
