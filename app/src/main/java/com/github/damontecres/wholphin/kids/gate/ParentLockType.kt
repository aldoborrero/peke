package com.github.damontecres.wholphin.kids.gate

/**
 * How grown-ups prove they are grown-ups at the parent gate: the kind of household code, which the server stores
 */
enum class ParentLockType {
    /**
     * A code of digits
     */
    NUMERIC,

    /**
     * A sequence of remote buttons: D-pad, digits and any extra buttons the remote has
     */
    REMOTE,
}
