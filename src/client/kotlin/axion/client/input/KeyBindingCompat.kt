package axion.client.input

import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object KeyBindingCompat {
    private val primitiveInt = Int::class.javaPrimitiveType!!

    fun create(translationKey: String, code: Int, categoryKey: String): KeyBinding {
        val categoryClass = resolveCategoryClass()

        if (categoryClass != null) {
            val category = createCategory(categoryClass, categoryKey)
            findConstructor(
                String::class.java,
                primitiveInt,
                categoryClass,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, code, category) as KeyBinding
            }
        }

        findConstructor(
            String::class.java,
            primitiveInt,
            String::class.java,
        )?.let { constructor ->
            return constructor.newInstance(translationKey, code, categoryKey) as KeyBinding
        }

        val inputUtilType = InputUtil.Type::class.java
        val keySym = InputUtil.Type.KEYSYM
        findConstructor(
            String::class.java,
            inputUtilType,
            primitiveInt,
            String::class.java,
        )?.let { constructor ->
            return constructor.newInstance(translationKey, keySym, code, categoryKey) as KeyBinding
        }
        if (categoryClass != null) {
            val category = createCategory(categoryClass, categoryKey)
            findConstructor(
                String::class.java,
                inputUtilType,
                primitiveInt,
                categoryClass,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, keySym, code, category) as KeyBinding
            }
            findConstructor(
                String::class.java,
                inputUtilType,
                primitiveInt,
                categoryClass,
                primitiveInt,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, keySym, code, category, 0) as KeyBinding
            }
        }

        error("Unsupported KeyBinding constructor shape")
    }

    private fun categoryIdentifier(categoryKey: String): Identifier {
        val suffix = categoryKey.removePrefix("keycategory.")
        val separatorIndex = suffix.indexOf('.')
        return if (separatorIndex > 0) {
            Identifier.of(suffix.substring(0, separatorIndex), suffix.substring(separatorIndex + 1))
        } else {
            Identifier.of("axion", suffix)
        }
    }

    private fun findConstructor(vararg parameterTypes: Class<*>): Constructor<*>? {
        return runCatching {
            KeyBinding::class.java.getConstructor(*parameterTypes)
        }.getOrNull()
    }

    private fun resolveCategoryClass(): Class<*>? {
        return KeyBinding::class.java.constructors.firstNotNullOfOrNull { constructor ->
            val parameterTypes = constructor.parameterTypes
            when {
                parameterTypes.size >= 3 &&
                    parameterTypes[0] == String::class.java &&
                    parameterTypes[1] == primitiveInt &&
                    parameterTypes[2] != String::class.java -> parameterTypes[2]

                parameterTypes.size >= 4 &&
                    parameterTypes[0] == String::class.java &&
                    parameterTypes[1] == InputUtil.Type::class.java &&
                    parameterTypes[2] == primitiveInt &&
                    parameterTypes[3] != String::class.java -> parameterTypes[3]

                else -> null
            }
        }
    }

    private fun createCategory(categoryClass: Class<*>, categoryKey: String): Any {
        val identifier = categoryIdentifier(categoryKey)
        findCategoryFactory(categoryClass)?.let { factory ->
            return factory.invoke(null, identifier)
        }
        return categoryClass.getConstructor(Identifier::class.java).newInstance(identifier)
    }

    private fun findCategoryFactory(categoryClass: Class<*>): Method? {
        return categoryClass.methods.firstOrNull { method ->
            method.name == "create" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Identifier::class.java &&
                method.returnType == categoryClass
        }
    }
}
