package org.dba.mongofx

/**
 * Represents the count of a specific product. Used in the product report.
 * @property product The name of the product.
 * @property count The number of occurrences.
 */
data class ProductCount(val product: String, val count: Int)

/**
 * Represents basic details of a UCID record. Used for displaying search results.
 * @property ucid The Unique Configuration ID.
 * @property exportDate The date the configuration was exported.
 */
data class UcidDetails(val ucid: String, val exportDate: String)

/**
 * Represents the raw result from a MongoDB aggregation pipeline for product counts.
 * @property product The name of the product (nullable from aggregation).
 * @property count The aggregated count.
 */
data class AggregationResult(val product: String?, val count: Int)

/**
 * Represents a part to be scanned for in an Excel BOM.
 * The quantity is mutable as it's updated during the scan.
 * @property quantity The number of this part found.
 * @property sku The Stock Keeping Unit (part number).
 * @property description A description of the part.
 */
data class Part(var quantity: Int, val sku: String, val description: String)

/**
 * Represents basic info for a UCID, used to locate its file for scanning.
 * @property ucid The Unique Configuration ID.
 * @property fileName The path to the associated file within the archive.
 */
data class UcidInfo(val ucid: String, val fileName: String)

/**
 * Represents the result of a SKU search, linking a UCID/file to a found quantity.
 * @property ucid The UCID where the SKU was found.
 * @property fileName The file where the SKU was found.
 * @property quantity The quantity of the SKU found.
 */
data class SkuSearchResult(val ucid: String, val fileName: String, val quantity: Int)