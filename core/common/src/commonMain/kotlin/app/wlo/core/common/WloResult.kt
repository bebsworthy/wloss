package app.wlo.core.common

/**
 * D8 result type: repositories and document stores return [WloResult] —
 * `Ok`/`Err` values, never thrown exceptions across module boundaries.
 * [Err] carries a sealed [AppError]; library exceptions are wrapped into
 * `AppError.Storage`/`AppError.Parse`/`AppError.External` at repository edges
 * (ARCHITECTURE.md §2.3 D8, §2.4 "Error handling").
 */
public sealed interface WloResult<out T> {
    public data class Ok<T>(
        public val value: T,
    ) : WloResult<T>

    public data class Err(
        public val error: AppError,
    ) : WloResult<Nothing>

    public companion object {
        public fun <T> ok(value: T): WloResult<T> = Ok(value)

        public fun err(error: AppError): WloResult<Nothing> = Err(error)
    }
}

/** Maps the success value, passing an [Err] through untouched. */
public inline fun <T, R> WloResult<T>.map(transform: (T) -> R): WloResult<R> =
    when (this) {
        is WloResult.Ok -> WloResult.Ok(transform(value))
        is WloResult.Err -> this
    }

/** Folds both branches into a single value (UI/state-holder entry point). */
public inline fun <T, R> WloResult<T>.fold(
    onOk: (T) -> R,
    onErr: (AppError) -> R,
): R =
    when (this) {
        is WloResult.Ok -> onOk(value)
        is WloResult.Err -> onErr(error)
    }

/** Null on failure — for optional reads where the caller has its own default. */
public fun <T> WloResult<T>.getOrNull(): T? =
    when (this) {
        is WloResult.Ok -> value
        is WloResult.Err -> null
    }

/** Chains a second fallible step (repository-internal composition). */
public inline fun <T, R> WloResult<T>.flatMap(transform: (T) -> WloResult<R>): WloResult<R> =
    when (this) {
        is WloResult.Ok -> transform(value)
        is WloResult.Err -> this
    }
