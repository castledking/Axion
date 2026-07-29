package axion.client.input

import axion.common.compat.VersionCompat
import net.minecraft.client.option.KeyBinding
import net.minecraft.util.Identifier
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object KeyBindingCompat {
    private val primitiveInt = Int::class.javaPrimitiveType!!

    /** Cache category instances by key so all keybindings sharing a category key
     *  reference the same object — MC groups keybindings by category identity. */
    private val categoryCache = mutableMapOf<String, Any>()

    fun create(translationKey: String, code: Int, categoryKey: String): KeyBinding {
        val categoryClass = resolveCategoryClass()
        val inputKeyClass = resolveInputKeyClass()

        // Path 1: (String, InputConstants.Key, Category) — 26.x Mojang KeyMapping
        if (inputKeyClass != null) {
            val keyForCode = keyForCode(inputKeyClass, code)

            if (categoryClass != null) {
                val category = categoryCache.getOrPut(categoryKey) { createCategory(categoryClass, categoryKey) }
                findConstructor(
                    String::class.java,
                    inputKeyClass,
                    categoryClass,
                )?.let { constructor ->
                    return constructor.newInstance(translationKey, keyForCode, category) as KeyBinding
                }
            }

            // Path 2: (String, InputConstants.Key, String) — 26.x string category fallback
            findConstructor(
                String::class.java,
                inputKeyClass,
                String::class.java,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, keyForCode, categoryKey) as KeyBinding
            }
        }

        // Path 3: (String, int, Category) — pre-26.x Yarn KeyBinding with typed category
        if (categoryClass != null) {
            val category = categoryCache.getOrPut(categoryKey) { createCategory(categoryClass, categoryKey) }
            findConstructor(
                String::class.java,
                primitiveInt,
                categoryClass,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, code, category) as KeyBinding
            }
        }

        // Path 4: (String, int, String) — pre-26.x Yarn KeyBinding
        findConstructor(
            String::class.java,
            primitiveInt,
            String::class.java,
        )?.let { constructor ->
            return constructor.newInstance(translationKey, code, categoryKey) as KeyBinding
        }

        // Path 5: (String, InputConstants.Type, int, String) — older Yarn KeyBinding
        val inputUtilType = resolveInputTypeClass()
        if (inputUtilType != null) {
            val keySym = keySymValue(inputUtilType) ?: error("Unsupported KeyBinding input type")
            findConstructor(
                String::class.java,
                inputUtilType,
                primitiveInt,
                String::class.java,
            )?.let { constructor ->
                return constructor.newInstance(translationKey, keySym, code, categoryKey) as KeyBinding
            }
            if (categoryClass != null) {
                val category = categoryCache.getOrPut(categoryKey) { createCategory(categoryClass, categoryKey) }
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
        }

        error("Unsupported KeyBinding constructor shape")
    }

    private fun categoryIdentifier(categoryKey: String): Identifier {
        val suffix = categoryKey.removePrefix("keycategory.")
        val separatorIndex = suffix.indexOf('.')
        return if (separatorIndex > 0) {
            VersionCompat.INSTANCE.identifierOf(suffix.substring(0, separatorIndex), suffix.substring(separatorIndex + 1))
        } else {
            VersionCompat.INSTANCE.identifierOf("axion", suffix)
        }
    }

    private fun findConstructor(vararg parameterTypes: Class<*>): Constructor<*>? {
        return runCatching {
            KeyBinding::class.java.getConstructor(*parameterTypes)
        }.getOrNull()
    }

    private fun resolveCategoryClass(): Class<*>? {
        // Look for KeyBinding.Category / KeyMapping.Category inner class
        val categoryInner = KeyBinding::class.java.declaredClasses.firstOrNull { it.simpleName == "Category" }
        if (categoryInner != null) return categoryInner

        // Fallback: detect non-String 3rd param in constructors with int 2nd param
        return KeyBinding::class.java.constructors.firstNotNullOfOrNull { constructor ->
            val params = constructor.parameterTypes
            when {
                params.size >= 3 &&
                    params[0] == String::class.java &&
                    params[1] == primitiveInt &&
                    params[2] != String::class.java -> params[2]

                params.size >= 4 &&
                    params[0] == String::class.java &&
                    params[1] == resolveInputTypeClass() &&
                    params[2] == primitiveInt &&
                    params[3] != String::class.java -> params[3]

                // 26.x: 3rd param is non-String when 2nd is InputConstants.Key
                params.size >= 3 &&
                    params[0] == String::class.java &&
                    params[1] == resolveInputKeyClass() &&
                    params[2] != String::class.java -> params[2]

                else -> null
            }
        }
    }

    private fun createCategory(categoryClass: Class<*>, categoryKey: String): Any {
        val identifier = categoryIdentifier(categoryKey)
        findCategoryFactory(categoryClass)?.let { factory ->
            return factory.invoke(null, identifier)
        }
        return runCatching {
            categoryClass.getConstructor(Identifier::class.java).newInstance(identifier)
        }.getOrNull() ?: runCatching {
            categoryClass.getConstructor(String::class.java).newInstance(categoryKey)
        }.getOrNull() ?: error("Cannot create category instance for $categoryClass")
    }

    private fun findCategoryFactory(categoryClass: Class<*>): Method? {
        return categoryClass.methods.firstOrNull { method ->
            method.name == "create" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Identifier::class.java &&
                method.returnType == categoryClass
        }
    }

    private fun resolveInputTypeClass(): Class<*>? {
        return KeyBinding::class.java.constructors
            .asSequence()
            .flatMap { constructor -> constructor.parameterTypes.asSequence() }
            .firstOrNull { type ->
                type.simpleName == "Type" &&
                    (type.enclosingClass?.simpleName == "InputUtil" || type.enclosingClass?.simpleName == "InputConstants")
            }
    }

    private fun keySymValue(inputTypeClass: Class<*>): Any? {
        return inputTypeClass.enumConstants
            ?.filterIsInstance<Enum<*>>()
            ?.firstOrNull { it.name == "KEYSYM" }
    }

    /** Resolves com.mojang.blaze3d.platform.InputConstants.Key (26.x). */
    private fun resolveInputKeyClass(): Class<*>? {
        val inputConstantsClass = runCatching {
            Class.forName("com.mojang.blaze3d.platform.InputConstants")
        }.getOrNull() ?: return null
        return inputConstantsClass.declaredClasses.firstOrNull { it.simpleName == "Key" }
    }

    /** Creates an InputConstants.Key instance from an int key code. */
    private fun keyForCode(inputKeyClass: Class<*>, code: Int): Any {
        val inputConstantsClass = inputKeyClass.enclosingClass

        // Try InputConstants.Type.KEYSYM.mapKey(code)
        val typeClass = inputConstantsClass?.declaredClasses?.firstOrNull { it.simpleName == "Type" }
        if (typeClass != null) {
            val keysym = typeClass.enumConstants?.firstOrNull {
                (it as Enum<*>).name == "KEYSYM"
            }
            if (keysym != null) {
                // Try Key(Type, int) constructor
                runCatching {
                    return inputKeyClass.getConstructor(typeClass, primitiveInt).newInstance(keysym, code)
                }

                // Try Type.mapKey(int) factory method
                runCatching {
                    return typeClass.getMethod("mapKey", primitiveInt).invoke(keysym, code)
                }
            }
        }

        // Fallback: InputConstants.UNKNOWN
        runCatching {
            return inputKeyClass.getField("UNKNOWN").get(null)
        }

        // Fallback: try constructor that takes only an int
        runCatching {
            return inputKeyClass.getConstructor(primitiveInt).newInstance(code)
        }

        error("Cannot create InputConstants.Key instance")
    }
}
