package axion.client.render.gpu

enum class ChunkedDrawResult {
    DREW,
    NO_VISIBLE_SECTIONS,
    NO_BUFFERS,
    FAILED,
    ;

    val handled: Boolean get() = this == DREW || this == NO_VISIBLE_SECTIONS
}
