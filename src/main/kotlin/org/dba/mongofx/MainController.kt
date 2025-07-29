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
import javafx.scene.control.CheckBox
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
    lateinit var downloadCheckBox: CheckBox

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
        val logger: Logger = LoggerFactory.getLogger("mongoFX")
    }

    private val controllerScope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())
    private val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()

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
            ucidTableView.placeholder = Label("Please enter an OPE to search")
            return
        }

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

        val ucidFileName = UCIDFileTextField.text
        val ucidDirName = UCIDdirTextField.text
        if (ucidFileName.isBlank() || (!downloadCheckBox.isSelected && ucidDirName.isBlank())) {
            partTableView.placeholder = Label("Please provide all required information")
            return
        }

        scanButton.isDisable = true
        partTableView.items.clear()
        partTableView.placeholder = Label("Scanning $ucidFileName...")

        controllerScope.launch {
            try {
                val foundParts = withContext(Dispatchers.IO) {
                    val searchList = loadSearchList()
                    if (searchList.isEmpty()) {
                        withContext(Dispatchers.JavaFx) {
                            partTableView.placeholder = Label("Could not load parts list to scan")
                        }
                        return@withContext emptyList()
                    }
                    val ucidFilePath = if (downloadCheckBox.isSelected) {
                        downloadPath.resolve("$ucidFileName.xlsx")
                    } else {
                        Paths.get(archiveBasePath, ucidDirName, "$ucidFileName.xlsx")
                    }
                    scanForParts(ucidFilePath.toString(), searchList)
                    searchList.filter { it.quantity > 0 }
                }

                partTableView.items = FXCollections.observableArrayList(foundParts)

                if (foundParts.isEmpty()) {
                    partTableView.placeholder = Label("No parts found in $ucidFileName")
                }

            } catch (e: Exception) {
                logger.error("Error scanning file: $ucidFileName", e)
                partTableView.placeholder = Label("Error scanning file. See logs for details")
            } finally {
                scanButton.isDisable = false
            }
        }

    }

    @FXML
    fun onUseDownloads() {
        UCIDdirTextField.isDisable = downloadCheckBox.isSelected
        if (!downloadCheckBox.isSelected) {
            UCIDdirTextField.clear()
            UCIDdirTextField.requestFocus()
        }
    }

    private fun loadSearchList(): List<Part> {
        val fileName = "/parts_to_scan.csv"
        try {
            val inputStream = MainController::class.java.getResourceAsStream(fileName)
                ?: throw IOException("Resource not found: $fileName")

            return inputStream.bufferedReader().useLines { lines ->
                lines
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val parts = line.split(',', limit = 2)
                        if (parts.size == 2) {
                            Part(quantity = 0, sku = parts[0].trim(), description = parts[1].trim())
                        } else {
                            logger.warn("Skipping malformed line in parts list: '$line'")
                            null
                        }
                    }.toList()
            }


        } catch (e: IOException) {
            logger.error("Failed to load search list from file $fileName", e)
            return emptyList()
        }

    }

    private fun scanForParts(filePath: String, searchList: List<Part>) {
        Files.newInputStream(Paths.get(filePath)).use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheet("ExpertBOM") ?: return
            var serverCount = 1

            for (rowIndex in 6..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue

                val skuCell = row.getCell(2)
                val skuValue = dataFormatter.formatCellValue(skuCell).trim()

                if (skuValue.isNotBlank()) {
                    searchList.find { it.sku.equals(skuValue, ignoreCase = true) }?.let { part ->
                        val quantCell = row.getCell(1)
                        if (quantCell != null && quantCell.cellType == CellType.NUMERIC) {
                            val quantityValue = quantCell.numericCellValue.toInt()
                            if (part.sku == "S4R96A") {
                                serverCount = if (quantityValue > 0) quantityValue else 1
                            }
                            part.quantity += quantityValue / serverCount
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