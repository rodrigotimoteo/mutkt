package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.InlinedBodyFilter
import com.github.rodrigotimoteo.mutkt.asm.filters.InlinedClassFilter
import com.github.rodrigotimoteo.mutkt.asm.filters.InlinedFinallyFilter
import com.github.rodrigotimoteo.mutkt.asm.filters.StringWhenFilter
import com.github.rodrigotimoteo.mutkt.asm.filters.WhenMappingsFilter

/** Every site filter; they are independent, so their order does not matter. */
internal object SiteFilters {
    val all: List<SiteFilter> =
        listOf(
            InlinedBodyFilter,
            InlinedClassFilter,
            InlinedFinallyFilter,
            StringWhenFilter,
            WhenMappingsFilter,
        )
}
