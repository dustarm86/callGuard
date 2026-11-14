package com.dustin.callguard.carrierdata

/**
 * where the phone can get carrier data, best first. the nanpa reports are the original and freshest, but have to be
 * built into the data files on the phone. the github copies are already built, so they download faster and
 * keep working if nanpa's site is down or refuses the request.
 */
enum class SourceKind { ZIP_REPORT, PREBUILT }

data class CarrierSource(
    val label: String,
    val kind: SourceKind,
    val exchangesUrl: String,
    val blocksUrl: String,
    val browserHeaders: Boolean = false,
)

object CarrierSources {
    private const val NANPA = "https://reports.nanpa.com/public"
    private const val EXCHANGE_ZIP = "$NANPA/CoCodeAssignment_Utilized_AllStates_Public.zip"
    private const val BLOCK_ZIP = "$NANPA/ThousandsBlockAssignment_All.zip"
    private const val REPO = "dustarm86/callGuard"

    val all = listOf(
        CarrierSource("nanpa reports", SourceKind.ZIP_REPORT, EXCHANGE_ZIP, BLOCK_ZIP),
        CarrierSource("nanpa reports, browser-style request", SourceKind.ZIP_REPORT, EXCHANGE_ZIP, BLOCK_ZIP, browserHeaders = true),
        CarrierSource(
            "github release copy", SourceKind.PREBUILT,
            "https://github.com/$REPO/releases/download/carrier-data/nanpa.bin",
            "https://github.com/$REPO/releases/download/carrier-data/blocks.bin",
        ),
        CarrierSource(
            "github data branch copy", SourceKind.PREBUILT,
            "https://raw.githubusercontent.com/$REPO/data/nanpa.bin",
            "https://raw.githubusercontent.com/$REPO/data/blocks.bin",
        ),
        CarrierSource(
            "jsDelivr cdn copy", SourceKind.PREBUILT,
            "https://cdn.jsdelivr.net/gh/$REPO@data/nanpa.bin",
            "https://cdn.jsdelivr.net/gh/$REPO@data/blocks.bin",
        ),
    )
}
