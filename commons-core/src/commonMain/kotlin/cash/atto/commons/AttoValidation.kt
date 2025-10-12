package cash.atto.commons

sealed interface AttoValidation {
    val isValid: Boolean get() = this is Ok

    fun getError(): String?

    object Ok : AttoValidation {
        override fun getError(): String? = null
    }

    class Error(
        private val error: String,
    ) : AttoValidation {
        override fun getError(): String = error
    }
}
