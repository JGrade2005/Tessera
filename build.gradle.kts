plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    // NEI is compile-time only: the two plugin classes are loaded by NEI itself,
    // so the mod still runs without it.
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.136-GTNH") { isTransitive = false }
}
