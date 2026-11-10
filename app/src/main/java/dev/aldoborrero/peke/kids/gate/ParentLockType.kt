package dev.aldoborrero.peke.kids.gate

/**
 * How grown-ups prove they are grown-ups at the parent gate: the kind of household code, which the server stores
 */
enum class ParentLockType {
    /**
     * A code of digits
     */
    NUMERIC,

    /**
     * A sequence of the four remote arrows, nothing else
     */
    REMOTE,
}
