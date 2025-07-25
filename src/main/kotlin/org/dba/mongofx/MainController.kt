package org.dba.mongofx

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.cell.PropertyValueFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.BsonType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class MainController {

    data class ProductCount(val product: String, val count: Int)
    data class UcidDetails(val ucid: String, val exportDate: String)
    data class UcidOpeQueryResult(val ucid: String, val exportDate: String)
    data class AggregationResult(val product: String?, val count: Int)
    data class Part(var quantity: Int, val sku: String, val description: String)

    @FXML
    lateinit var UCIDdirTextField: TextField

    @FXML
    lateinit var quantityColumn: TableColumn<Part, Int>

    @FXML
    lateinit var descriptionColumn: TableColumn<Part, String>

    @FXML
    lateinit var skuColumn: TableColumn<Part, String>

    @FXML
    lateinit var partTableView: TableView<Part>

    @FXML
    lateinit var scanButton: Button

    @FXML
    lateinit var UCIDFileTextField: TextField


    @FXML
    lateinit var exportDateColumn: TableColumn<UcidDetails, String>

    @FXML
    lateinit var ucidColumn: TableColumn<UcidDetails, String>

    @FXML
    lateinit var ucidTableView: TableView<UcidDetails>

    @FXML
    lateinit var opeSearchButton: Button

    @FXML
    lateinit var opeTextField: TextField

    @FXML
    lateinit var textFieldDate: TextField

    @FXML
    lateinit var buttonLoad: Button

    @FXML
    lateinit var buttonQuit: Button

    @FXML
    lateinit var buttonStore: Button

    @FXML
    lateinit var textFieldFileName: TextField

    @FXML
    lateinit var textFieldOPE: TextField

    @FXML
    lateinit var textFieldCustomer: TextField

    @FXML
    lateinit var textFieldUCID: TextField

    @FXML
    lateinit var textFieldProduct: TextField

    @FXML
    lateinit var textAreaResult: TextArea

    @FXML
    lateinit var countColumn: TableColumn<ProductCount, Int>

    @FXML
    lateinit var productColumn: TableColumn<ProductCount, String>

    @FXML
    lateinit var productTableView: TableView<ProductCount>

    @FXML
    lateinit var monthComboBox: ComboBox<String>

    @FXML
    lateinit var yearTextField: TextField

    @FXML
    lateinit var reportButton: Button

    companion object {
        val logger: Logger = LoggerFactory.getLogger(MainController::class.java)
    }

    private val controllerScope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())

    val archiveBasePath =
        "C:\\Users\\albertd\\OneDrive - Hewlett Packard Enterprise\\HPE\\Early Quotes\\2025"
    val downloadPath = Paths.get("C:\\Users\\albertd\\Downloads")

    @FXML
    fun initialize() {
        productColumn.cellValueFactory = PropertyValueFactory("product")
        countColumn.cellValueFactory = PropertyValueFactory("count")

        ucidColumn.cellValueFactory = PropertyValueFactory("ucid")
        exportDateColumn.cellValueFactory = PropertyValueFactory("exportDate")

        skuColumn.cellValueFactory = PropertyValueFactory("sku")
        descriptionColumn.cellValueFactory = PropertyValueFactory("description")
        quantityColumn.cellValueFactory = PropertyValueFactory("quantity")


        val months = Month.entries.map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
        monthComboBox.items = FXCollections.observableArrayList(months)
        yearTextField.text = LocalDate.now().year.toString()
    }

    @FXML
    fun loadData() {
        val ucidToFind = textFieldUCID.text
        if (ucidToFind.isBlank()) {
            textAreaResult.text = "Please enter a UCID to load."
            return
        }
        controllerScope.launch {

            val config = withContext(Dispatchers.IO) {
                MongoManage.collection.find(eq("ucid", ucidToFind)).firstOrNull()
            }
            if (config != null) {
                textFieldOPE.text = config.ope
                textFieldProduct.text = config.product
                textFieldFileName.text = config.fileName
                textFieldCustomer.text = config.customer
                textFieldDate.text = config.exportDate
                textAreaResult.text = "Successfully loaded data for customer: ${config.customer}"

            } else {
                textAreaResult.text = "No data found for UCID: $ucidToFind"
            }
        }
    }

    @FXML
    fun storeConfigData() {
        buttonStore.isDisable = true
        textAreaResult.text = "Starting to process files...\n"

        controllerScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    processExcelFiles()
                }
                textAreaResult.appendText("Finished processing all files.\n")
            } catch (e: Exception) {
                logger.error("A critical error occurred during file processing.", e)
                textAreaResult.appendText("ERROR: An unexpected error occurred. Check logs.\n")
            } finally {
                buttonStore.isDisable = false
            }
        }
    }

    private suspend fun processExcelFiles() {

        if (!Files.isDirectory(downloadPath)) {
            logger.error("Downloads directory not found: $downloadPath")
            withContext(Dispatchers.JavaFx) { textAreaResult.appendText("ERROR: Downloads directory not found.\n") }
            return
        }

        val excelFiles = findExcelFiles(downloadPath)
        withContext(Dispatchers.JavaFx) { textAreaResult.appendText("Found ${excelFiles.size} Excel files to process.\n") }

        for (filePath in excelFiles) {
            try {
                val (newConfig, dirName) = parseConfigFromFile(filePath) ?: continue

                MongoManage.collection.insertOne(newConfig)
                logger.info("Successfully inserted config from: ${newConfig.fileName}")

                moveFileToArchive(filePath, dirName)

                withContext(Dispatchers.JavaFx) {
                    textAreaResult.appendText("Processed and archived: ${filePath.fileName}\n")
                }
            } catch (e: Exception) {
                logger.error("Failed to process file $filePath", e)
                withContext(Dispatchers.JavaFx) {
                    textAreaResult.appendText("ERROR processing file: ${filePath.fileName}. See logs.\n")
                }
            }
        }
    }

    private fun findExcelFiles(directoryPath: Path): List<Path> {
        return try {
            Files.walk(directoryPath, 1)
                .filter {
                    Files.isRegularFile(it) && (it.toString().endsWith(".xlsx") || it.toString().endsWith(".xls"))
                }
                .toList()
        } catch (e: IOException) {
            logger.error("Error walking directory $directoryPath", e)
            emptyList()
        }
    }

    private fun moveFileToArchive(sourcePath: Path, dirName: String) {
        try {
            val archiveDir = Paths.get(archiveBasePath, dirName)
            Files.createDirectories(archiveDir)
            val destinationPath = archiveDir.resolve(sourcePath.fileName)
            Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            logger.error("Failed to archive file: $sourcePath", e)
        }
    }

    private fun parseConfigFromFile(filePath: Path): Pair<Config, String>? {
        try {
            FileInputStream(filePath.toFile()).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(0)
                    val firstCell = sheet.getRow(0)?.getCell(0)

                    // Guard Clause: Exit early if not a valid config file
                    if (firstCell?.cellType != CellType.STRING || !firstCell.stringCellValue.startsWith("Config")) {
                        return null
                    }

                    val headerWords = firstCell.stringCellValue.split(" ")
                    val product = headerWords.getOrNull(3) ?: "Unknown"
                    val dirName = headerWords.getOrNull(2) ?: "Misc"

                    // Using safe calls (?.) and elvis operators (?:) to prevent NullPointerExceptions
                    val ucid = sheet.getRow(1)?.getCell(0)?.stringCellValue?.split(" ")?.getOrNull(1) ?: ""
                    val exportDate = sheet.getRow(2)?.getCell(0)?.stringCellValue?.split(" ")?.getOrNull(2) ?: ""
                    val ope = sheet.getRow(3)?.getCell(0)?.stringCellValue?.substringAfter(": ") ?: ""
                    val customer = sheet.getRow(4)?.getCell(0)?.stringCellValue?.substringAfter(": ") ?: ""
                    val fileName = dirName + "\\" + filePath.fileName.toString()

                    val archiveDir = Paths.get(archiveBasePath, dirName)
                    val isUnique = if (Files.isDirectory(archiveDir)) "0" else "1"


                    // Return a new, immutable Config object
                    val config = Config(
                        product = product, ucid = ucid, exportDate = exportDate, ope = ope,
                        customer = customer, fileName = fileName, unique = isUnique
                    )
                    return config to dirName
                }
            }
        } catch (e: IOException) {
            logger.error("Could not read or parse Excel file: $filePath", e)
            return null
        }
    }

    @FXML
    fun generateProductReport() {
        val selectedMonthName = monthComboBox.value
        val year = yearTextField.text

        if (selectedMonthName.isNullOrBlank() || year.isNullOrBlank()) {
            productTableView.placeholder = Label("Please select a month and enter a year")
            return
        }

        reportButton.isDisable = true
        productTableView.items.clear()
        productTableView.placeholder = Label("Loading report...")

        controllerScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    fetchProductCounts(selectedMonthName, year)
                }

                productTableView.items = FXCollections.observableArrayList(results)
                if (results.isEmpty()) {
                    productTableView.placeholder = Label("No data found for the selected period.")
                }

            } catch (e: Exception) {
                logger.error("Failed to generate product report", e)
                productTableView.placeholder = Label("Error loading report. See logs for details.")
            } finally {
                // Re-enable the button
                reportButton.isDisable = false
            }
        }
    }

    suspend fun fetchProductCounts(monthName: String, year: String): List<ProductCount> {

        val monthNumber = Month.entries.first { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) == monthName }.value
        val monthString = monthNumber.toString().padStart(2, '0')

        // Create a regex to match dates like "YYYY-MM-DD"
        val dateRegex = "^$year-$monthString-.*"

        // Define the aggregation pipeline
        val pipeline = listOf(
            // Stage 1: Find documents that match the date AND have a non-null product field.
            Aggregates.match(
                Filters.and(
                    Filters.regex("exportDate", dateRegex),
                    Filters.type("product", BsonType.STRING),
                )
            ),
            // Stage 2: Group by the 'product' field and count the items in each group
            Aggregates.group("\$product", Accumulators.sum("count", 1)),
            // Stage 3: Reshape the output to be more friendly
            Aggregates.project(
                Projections.fields(
                    Projections.computed("product", "\$_id"),
                    Projections.include("count"),
                    Projections.excludeId()
                )
            )
        )

        // Execute the pipeline and map the results to our UI data class
        return MongoManage.collection
            .aggregate<AggregationResult>(pipeline)
            .toList()
            .filter { it.product != null }
            .map { ProductCount(it.product!!, it.count) } // Map to the TableView's data class
            .sortedByDescending { it.count } // Sort for better presentation
    }

    @FXML
    fun searchByOpe() {
        val opeToFind = opeTextField.text

        if (opeToFind.isNullOrBlank()) {
            ucidTableView.placeholder = Label("Please enter a UCID")
            return
        }
        if (opeToFind.isNotBlank()) {
            opeSearchButton.isDisable = true
            ucidTableView.items.clear()
            ucidTableView.placeholder = Label("Loading details for OPE: $opeToFind...")

            controllerScope.launch {
                try {
                    val results = withContext(Dispatchers.IO) {
                        fetchUcidDetailsForOpe(opeToFind)
                    }
                    ucidTableView.items = FXCollections.observableArrayList(results)
                    if (results.isEmpty()) {
                        ucidTableView.placeholder = Label("No data found for OPE: $opeToFind.")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to fetch details for OPE: $opeToFind", e)
                    ucidTableView.placeholder = Label("Error loading details. See logs for details.")
                } finally {
                    opeSearchButton.isDisable = false
                }
            }
        }
    }

    private suspend fun fetchUcidDetailsForOpe(ope: String): List<UcidDetails> {
        val mongoResults = MongoManage.collection
            .withDocumentClass<UcidOpeQueryResult>()
            .find(eq("ope", ope))
            .projection(
                Projections.fields(
                    Projections.include("ucid", "exportDate"),
                    Projections.excludeId()
                )
            )
            .toList()
        return mongoResults.map { result ->
            UcidDetails(result.ucid, result.exportDate)
        }.sortedByDescending { it.exportDate }
    }

    @FXML
    fun scanFile() {
        val searchList = listOf(
            Part(quantity = 0, sku = "S4R96A", description = "XD685 DLC Server"),
            Part(quantity = 0, sku = "P73359-B21", description = "EPYC 9535"),
            Part(quantity = 0, sku = "P73356-B21", description = "EPYC 9645"),
            Part(quantity = 0, sku = "P73361-B21", description = "EPYC 9455"),
            Part(quantity = 0, sku = "P64986-H21", description = "64 GB"),
            Part(quantity = 0, sku = "P64987-H21", description = "96 GB"),
            Part(quantity = 0, sku = "P64988-H21", description = "128 GB"),
            Part(quantity = 0, sku = "S4X30A", description = "3.84 TB NVMe"),
            Part(quantity = 0, sku = "S4X32A", description = "7.68 TB NVMe"),
            Part(quantity = 0, sku = "P26253-B21", description = "BCM 57416 400 Gb"),
            Part(quantity = 0, sku = "P45641-H23", description = "CX7 400 Gb"),
            Part(quantity = 0, sku = "P66386-H21", description = "B3220 DPU"),
            Part(quantity = 0, sku = "S4W08A", description = "NVIDIA HGX B300"),
            Part(quantity = 0, sku = "S3W20A", description = "NVIDIA HGX B200"),
        )
        val ucidFileName = UCIDFileTextField.text
        val ucidDirName = UCIDdirTextField.text
        if (ucidFileName.isNullOrBlank() || ucidDirName.isNullOrBlank()) {
            partTableView.placeholder = Label("Please enter a UCID File")
            return
        }
        scanButton.isDisable = true
        partTableView.items.clear()
        partTableView.placeholder = Label("Scanning $ucidFileName...")

        controllerScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ucidFilePath = Paths.get(archiveBasePath, ucidDirName, ucidFileName).toString() + ".xlsx"
                    scanForParts(ucidFilePath, searchList)
                }
                val foundParts = searchList.filter { it.quantity > 0 }
                partTableView.items = FXCollections.observableArrayList(foundParts)

                if (foundParts.isEmpty()) {
                    partTableView.placeholder = Label("No parts found in $ucidFileName")
                }

            } catch (e: Exception) {
                logger.error("Error scanning file: $ucidFileName", e)
            } finally {
                scanButton.isDisable = false
            }
        }

    }

    private fun scanForParts(filePath: String, searchList: List<Part>) {
        FileInputStream(filePath).use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheet("ExpertBOM")
            var serverCount = 1

            sheet.forEach { row ->
                val cell = row.getCell(2)
                cell?.stringCellValue?.let { cellValue ->
                    searchList.forEach { part ->
                        if (cellValue.equals(part.sku, ignoreCase = true)) {
                            val quantCell = row.getCell(1)
                            if (part.sku == "S4R96A")
                                serverCount = quantCell.numericCellValue.toInt()
                            part.quantity += quantCell.numericCellValue.toInt() / serverCount
                        }
                    }
                }
            }
        }
    }

    @FXML
    fun quit() {
        Platform.exit()

    }
}