package com.wynime.app.trace

import kotlin.concurrent.Volatile

interface ErrorReportScope {
    fun setTag(key: String, value: String)
}

interface IErrorReport {
    fun captureMessage(
        message: String,

        config: ErrorReportScope.() -> Unit = {}
    )

    fun captureException(
        throwable: Throwable,

        config: ErrorReportScope.() -> Unit = {}
    )
}

val ErrorReport get() = ErrorReportHolder._errorReport

object ErrorReportHolder {
    @Suppress("ObjectPropertyName")
    @Volatile
    internal var _errorReport: IErrorReport = NoopErrorReport

    fun init(errorReport: IErrorReport) {
        _errorReport = errorReport
    }
}

private object NoopErrorReport : IErrorReport {
    override fun captureMessage(message: String, config: ErrorReportScope.() -> Unit) {

    }

    override fun captureException(throwable: Throwable, config: ErrorReportScope.() -> Unit) {

    }
}
