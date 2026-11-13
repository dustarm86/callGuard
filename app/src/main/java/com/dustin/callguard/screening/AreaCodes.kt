package com.dustin.callguard.screening

/**
 * offline area code lookup. coverage is deliberately partial: a code that isn't in the table
 * returns null and the ui says so, rather than guessing. overlay codes share a region with the
 * code they overlay. canadian and caribbean codes are not included yet.
 */
object AreaCodes {

    val tollFreeCodes: Set<String> = setOf("800", "833", "844", "855", "866", "877", "888")

    // north american area codes outside the us and canada that bill like international calls, which
    // is why "one ring" callback scams use them. us territories (puerto rico, guam, the us virgin islands) are
    // domestic calls and are not listed.
    private val callbackRiskCodes: Map<String, String> = mapOf(
        "242" to "the Bahamas", "246" to "Barbados", "264" to "Anguilla", "268" to "Antigua and Barbuda",
        "284" to "the British Virgin Islands", "345" to "the Cayman Islands", "441" to "Bermuda",
        "473" to "Grenada", "649" to "Turks and Caicos", "658" to "Jamaica", "664" to "Montserrat",
        "721" to "Sint Maarten", "758" to "Saint Lucia", "767" to "Dominica", "784" to "Saint Vincent",
        "809" to "the Dominican Republic", "829" to "the Dominican Republic", "849" to "the Dominican Republic",
        "868" to "Trinidad and Tobago", "869" to "Saint Kitts and Nevis", "876" to "Jamaica",
    )

    /** the country when the area code is one that a callback scam can bill like an international call, else null. */
    fun callbackRisk(normalizedNumber: String?): String? = areaCodeOf(normalizedNumber)?.let { callbackRiskCodes[it] }

    fun areaCodeOf(normalizedNumber: String?): String? =
        normalizedNumber?.takeIf { it.length == 10 }?.substring(0, 3)

    fun isTollFree(normalizedNumber: String?): Boolean =
        areaCodeOf(normalizedNumber) in tollFreeCodes

    /** a readable region like "Rochester area, NY", "toll-free", or null when the code is unknown. */
    fun lookup(normalizedNumber: String?): String? {
        val areaCode = areaCodeOf(normalizedNumber) ?: return null
        if (areaCode in tollFreeCodes) return "toll-free"
        return regions[areaCode]
    }

    private val regions: Map<String, String> by lazy {
        buildMap {
            for (line in TABLE.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val (codes, label) = trimmed.split('|', limit = 2)
                for (code in codes.split(',')) put(code.trim(), label.trim())
            }
        }
    }

    // format: code,code|region
    private const val TABLE = """
        212,332,646,917|Manhattan, NY
        347,718,929|New York City (outer boroughs), NY
        315,680|north-central NY (Syracuse, Utica, Watertown)
        516,363|Nassau County (Long Island), NY
        631,934|Suffolk County (Long Island), NY
        518,838|Albany area, NY
        585|Rochester area, NY
        607|Binghamton and Southern Tier, NY
        716|Buffalo area, NY
        845,329|Hudson Valley, NY
        914|Westchester County, NY
        201,551|northern NJ (Jersey City, Hackensack)
        973,862|Newark area, NJ
        908|Union County, NJ
        732,848|central NJ
        609,640|Trenton and Atlantic City, NJ
        856|Camden area, NJ
        215,267,445|Philadelphia, PA
        610,484|Philadelphia suburbs and Allentown, PA
        412,878|Pittsburgh, PA
        724|western PA
        717|Harrisburg and Lancaster, PA
        570|Scranton and Wilkes-Barre, PA
        814|Erie and Altoona, PA
        203,475|southwest Connecticut (Bridgeport, New Haven)
        860,959|Hartford area, CT
        617,857|Boston, MA
        508,774|Worcester and Framingham, MA
        781,339|Boston suburbs, MA
        978,351|Lowell and Lawrence, MA
        413|western MA (Springfield)
        401|Rhode Island
        603|New Hampshire
        207|Maine
        802|Vermont
        302|Delaware
        202|Washington, DC
        301,240|Maryland (DC suburbs and western MD)
        410,443,667|Baltimore area, MD
        703,571|northern Virginia
        804|Richmond, VA
        757|Norfolk and Virginia Beach, VA
        540|Roanoke and Fredericksburg, VA
        434|Lynchburg and Charlottesville, VA
        404,678,470,770|Atlanta area, GA
        706,762|northern and western GA
        912|Savannah, GA
        478|central GA
        305,786|Miami, FL
        954,754|Fort Lauderdale, FL
        561|Palm Beach, FL
        407,321,689|Orlando area, FL
        813,656|Tampa, FL
        727|St. Petersburg and Clearwater, FL
        904|Jacksonville, FL
        850|Florida Panhandle
        352|Gainesville and Ocala, FL
        239|southwest FL (Fort Myers, Naples)
        941|Sarasota, FL
        863|Lakeland, FL
        704,980|Charlotte, NC
        919,984|Raleigh and Durham, NC
        336|Greensboro and Winston-Salem, NC
        828|Asheville, NC
        910|Fayetteville and Wilmington, NC
        803,839|Columbia, SC
        843,854|Charleston and Myrtle Beach, SC
        864|Greenville, SC
        615,629|Nashville, TN
        901|Memphis, TN
        865|Knoxville, TN
        423|Chattanooga, TN
        502|Louisville, KY
        859|Lexington and northern KY
        270,364|western KY
        205,659|Birmingham, AL
        251|Mobile, AL
        256,938|northern AL
        334|Montgomery, AL
        601,769|Jackson, MS
        504|New Orleans, LA
        225|Baton Rouge, LA
        337|Lafayette, LA
        318|Shreveport, LA
        985|southeast LA
        214,469,972,945|Dallas, TX
        817,682|Fort Worth, TX
        713,281,832,346|Houston, TX
        210,726|San Antonio, TX
        512,737|Austin, TX
        915|El Paso, TX
        806|Lubbock and Amarillo, TX
        361|Corpus Christi, TX
        409|Beaumont and Galveston, TX
        254|Waco, TX
        940|Denton and Wichita Falls, TX
        903,430|Tyler, TX
        432|Midland and Odessa, TX
        312,872|Chicago, IL
        773|Chicago, IL
        708|Chicago south and west suburbs, IL
        630,331|Chicago western suburbs, IL
        847,224|Chicago northern suburbs, IL
        815,779|Rockford, IL
        217|Springfield, IL
        309|Peoria, IL
        313|Detroit, MI
        248,947|Oakland County, MI
        586|Macomb County, MI
        734|Ann Arbor, MI
        810|Flint, MI
        616|Grand Rapids, MI
        517|Lansing, MI
        269|southwest MI
        231|northwest MI
        989|central MI
        906|Upper Peninsula, MI
        216|Cleveland, OH
        440|northeast OH
        330,234|Akron and Youngstown, OH
        614,380|Columbus, OH
        513,283|Cincinnati, OH
        937,326|Dayton, OH
        419,567|Toledo, OH
        740,220|southeast OH
        317,463|Indianapolis, IN
        574|South Bend, IN
        765|central IN
        812,930|southern IN
        219|northwest IN
        260|Fort Wayne, IN
        414|Milwaukee, WI
        262|Milwaukee suburbs, WI
        608|Madison, WI
        920,274|Green Bay, WI
        715,534|northern WI
        612|Minneapolis, MN
        651|St. Paul, MN
        763|Minneapolis northern suburbs, MN
        952|Minneapolis southern suburbs, MN
        507|southern MN
        218|northern MN (Duluth)
        320|central MN
        515|Des Moines, IA
        319|eastern IA
        563|Davenport, IA
        641|central IA
        712|western IA
        314|St. Louis, MO
        636|St. Louis suburbs, MO
        816|Kansas City, MO
        417|Springfield, MO
        573|central MO
        660|northern MO
        316|Wichita, KS
        785|northern KS (Topeka)
        913|Kansas City, KS
        402,531|Omaha and Lincoln, NE
        308|western NE
        701|North Dakota
        605|South Dakota
        303,720,983|Denver, CO
        970|northern and western CO
        719|Colorado Springs, CO
        801,385|Salt Lake City, UT
        435|Utah (outside Salt Lake)
        602,480,623|Phoenix, AZ
        520|Tucson, AZ
        928|northern AZ
        505|Albuquerque, NM
        575|southern NM
        702,725|Las Vegas, NV
        775|Reno and northern NV
        208,986|Idaho
        406|Montana
        307|Wyoming
        206|Seattle, WA
        253|Tacoma, WA
        425|Seattle eastside, WA
        360,564|western WA
        509|Spokane and eastern WA
        503,971|Portland, OR
        541|Oregon (outside Portland)
        213,323|Los Angeles, CA
        310,424|west Los Angeles, CA
        818,747|San Fernando Valley, CA
        626|Pasadena, CA
        661|Bakersfield and Santa Clarita, CA
        714|Orange County, CA
        949|south Orange County, CA
        951|Riverside, CA
        909|San Bernardino, CA
        562|Long Beach, CA
        760,442|inland and desert southern CA
        805|Santa Barbara and Ventura, CA
        619|San Diego, CA
        858|San Diego north, CA
        415,628|San Francisco, CA
        510,341|Oakland, CA
        408,669|San Jose, CA
        650|San Mateo and Palo Alto, CA
        707|North Bay, CA
        831|Santa Cruz and Monterey, CA
        925|Contra Costa, CA
        559|Fresno, CA
        530|northern CA
        209|Stockton and Modesto, CA
        916,279|Sacramento, CA
        808|Hawaii
        907|Alaska
    """
}
