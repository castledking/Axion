package axion.client.render.gpu

import org.joml.Matrix4f

/**
 * Six camera-space frustum planes extracted from a `proj * modelView`
 * matrix, with the camera-relative offset baked back into world space so
 * call-sites can test world-absolute AABBs directly.
 *
 * Each plane is stored as `(a, b, c, d)` representing `a*x + b*y + c*z + d ≥ 0`
 * for points inside the frustum. The standard Gribb–Hartmann extraction:
 *
 *   left   = m4 + m1
 *   right  = m4 - m1
 *   bottom = m4 + m2
 *   top    = m4 - m2
 *   near   = m4 + m3
 *   far    = m4 - m3
 *
 * where m1..m4 are the rows of `proj * modelView`.
 *
 * The model-view matrix has the camera position pre-subtracted (vanilla MC's
 * convention), so the resulting plane equations are camera-relative. We undo
 * that by translating each plane by `+cameraPos`, which simplifies to
 * adjusting only the `d` component:
 *
 *   d_world = d_camera - (a*camX + b*camY + c*camZ)
 */
class FrustumPlanes private constructor(private val planes: FloatArray) {

    /**
     * True when the world-space axis-aligned box `[min, max]` is at least
     * partially inside the frustum. Uses the "p-vertex" test: for each plane
     * we evaluate the box corner most likely to be inside (the one in the
     * direction of the plane normal). If even that corner is outside, the
     * whole box is outside.
     */
    fun isAabbVisible(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
    ): Boolean {
        var i = 0
        while (i < 24) {
            val a = planes[i]
            val b = planes[i + 1]
            val c = planes[i + 2]
            val d = planes[i + 3]
            // Pick the box corner that maximises a*x + b*y + c*z.
            val px = if (a >= 0f) maxX else minX
            val py = if (b >= 0f) maxY else minY
            val pz = if (c >= 0f) maxZ else minZ
            if (a * px + b * py + c * pz + d < 0f) {
                return false
            }
            i += 4
        }
        return true
    }

    companion object {
        fun fromViewProj(
            viewProj: Matrix4f,
            cameraX: Double,
            cameraY: Double,
            cameraZ: Double,
        ): FrustumPlanes {
            // joml stores matrices column-major; m(row, col).
            val planes = FloatArray(24)
            // Helper to write a plane: row3 +/- rowI, then offset for camera.
            extract(planes, 0, viewProj, +1) // left:   m4 + m1
            extract(planes, 4, viewProj, -1) // right:  m4 - m1
            extractY(planes, 8, viewProj, +1) // bottom
            extractY(planes, 12, viewProj, -1) // top
            extractZ(planes, 16, viewProj, +1) // near
            extractZ(planes, 20, viewProj, -1) // far

            // Re-base from camera-relative to world-absolute by shifting d
            // by `-(a*camX + b*camY + c*camZ)` — equivalent to translating
            // each plane by +cameraPos.
            val cx = cameraX.toFloat()
            val cy = cameraY.toFloat()
            val cz = cameraZ.toFloat()
            var i = 0
            while (i < 24) {
                planes[i + 3] -= planes[i] * cx + planes[i + 1] * cy + planes[i + 2] * cz
                i += 4
            }

            // Normalize for consistent p-vertex distance interpretation.
            i = 0
            while (i < 24) {
                val a = planes[i]
                val b = planes[i + 1]
                val c = planes[i + 2]
                val len = kotlin.math.sqrt(a * a + b * b + c * c)
                if (len > 0f) {
                    val inv = 1f / len
                    planes[i] = a * inv
                    planes[i + 1] = b * inv
                    planes[i + 2] = c * inv
                    planes[i + 3] = planes[i + 3] * inv
                }
                i += 4
            }
            return FrustumPlanes(planes)
        }

        private fun extract(planes: FloatArray, off: Int, m: Matrix4f, sign: Int) {
            // m4 + sign * m1: take row3 + sign*row0 of viewProj.
            planes[off] = m.m03() + sign * m.m00()
            planes[off + 1] = m.m13() + sign * m.m10()
            planes[off + 2] = m.m23() + sign * m.m20()
            planes[off + 3] = m.m33() + sign * m.m30()
        }

        private fun extractY(planes: FloatArray, off: Int, m: Matrix4f, sign: Int) {
            planes[off] = m.m03() + sign * m.m01()
            planes[off + 1] = m.m13() + sign * m.m11()
            planes[off + 2] = m.m23() + sign * m.m21()
            planes[off + 3] = m.m33() + sign * m.m31()
        }

        private fun extractZ(planes: FloatArray, off: Int, m: Matrix4f, sign: Int) {
            planes[off] = m.m03() + sign * m.m02()
            planes[off + 1] = m.m13() + sign * m.m12()
            planes[off + 2] = m.m23() + sign * m.m22()
            planes[off + 3] = m.m33() + sign * m.m32()
        }
    }
}
