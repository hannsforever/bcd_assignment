package application;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class DropdownOption {
    private String label;
    private String value;

    public DropdownOption(String code) {
        this.label = code;
        this.value = code;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return label;
    }
}

public class AssignSupplierPage {
	private static String masterFolder = "master";
	private static String fileName = masterFolder+ "/chain.bin";
	
    private TextField supplierNameField;
    private TextField supplierAddressField;
    private ComboBox<DropdownOption> productCodeComboBox;
    private Button assignSupplierButton;
    private Button backButton;
    private String productInformationFilePath;
    private List<DropdownOption> productCodes = new ArrayList<>();

    public AssignSupplierPage(String productInformationFilePath) {
        this.productInformationFilePath = productInformationFilePath;
    }

    public void display() throws Exception {
        Stage stage = new Stage();

        // Create GUI components
        supplierNameField = new TextField();
        supplierAddressField = new TextField();
        productCodeComboBox = new ComboBox<>();
        assignSupplierButton = new Button("Assign Supplier");
        backButton = new Button("Back");

        // Configure event handlers
        assignSupplierButton.setOnAction(event -> {
			try {
				assignSupplier(stage);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
        backButton.setOnAction(event -> stage.close());

        // Create layout
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(
                new Label("Supplier Name:"),
                supplierNameField,
                new Label("Supplier Address:"),
                supplierAddressField,
                new Label("Product Code:"),
                productCodeComboBox,
                assignSupplierButton,
                backButton
        );

        // Set up scene and stage
        Scene scene = new Scene(root, 300, 275);
        stage.setTitle("Assign Supplier");
        stage.setScene(scene);
        stage.show();

        // Load the engine oil codes
        loadEngineOilCodes();
    }

    private void extractProductCodes(String filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            PredefinedCharsSecretKey secretKey = PredefinedCharsSecretKey.getInstance();
            Key preSecretKey = secretKey.getPreSecretKey();
            Symmetric symm = new Symmetric();

            while ((line = reader.readLine()) != null) {
                String data = symm.decrypt(line, preSecretKey);

                if (data.startsWith("ProductInformation")) {
                    int startIndex = data.indexOf("code=") + 5;
                    int endIndex = data.indexOf(",", startIndex);
                    if (endIndex == -1) {
                        endIndex = data.indexOf(" ", startIndex);
                        if (endIndex == -1) {
                            endIndex = data.length();
                        }
                    }
                    String code = data.substring(startIndex, endIndex).trim();
                    productCodes.add(new DropdownOption(code));  // Create DropdownOption and add to the list
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void loadEngineOilCodes() throws Exception {
        extractProductCodes(productInformationFilePath);  // Pass the file path as an argument
        ObservableList<DropdownOption> codeList = FXCollections.observableArrayList();
        
        // Add the product codes in reverse order to the codeList
        for (int i = productCodes.size() - 1; i >= 0; i--) {
            codeList.add(productCodes.get(i));
        }
        
        productCodeComboBox.setItems(codeList);
    }

    private void assignSupplier(Stage stage) throws Exception {
        String supplierName = supplierNameField.getText();
        String supplierAddress = supplierAddressField.getText();
        DropdownOption selectedOption = productCodeComboBox.getValue();

        if (supplierName.isEmpty() || supplierAddress.isEmpty() || selectedOption == null) {
            // Display an error message if any of the fields are empty
            System.out.println("Please fill in all the fields.");
        } else {
            // Get the product information based on the selected product code
            ProductInformation productInformation = getProductInformation(selectedOption.getValue());

            // Set the supplier information
            SupplierInformation supplierInformation = new SupplierInformation(supplierName, supplierAddress);

            Blockchain blockchain;
            
            // Check if the master folder exists, create it if it doesn't
            if(!new File(masterFolder).exists()) {
    			new File(masterFolder).mkdir();
    			
    			// Initialize the blockchain with a genesis block
    	        blockchain = Blockchain.getInstance(fileName);
    	        blockchain.genesis();
    		} else {
    			blockchain = Blockchain.getInstance(fileName);
    		}
            
            // Set the transaction date time
            String dateTimeNow = LocalDateTime.now().toString();

            EngineOilTransaction eoTranx = new EngineOilTransaction();
            String tranx = productInformation.toString() + ", " + supplierInformation.toString() + ", " + dateTimeNow;
            eoTranx.add(tranx);
            
            System.out.println(eoTranx.getEngineOilTransaction());

            // Write transaction file with the EngineOilTransaction
            writeTransactionFile(eoTranx.toString(), dateTimeNow);
            
            // Generate digital signature
            if(generateDigitalSignature(eoTranx.toString(), dateTimeNow)) {
                
                Block genesis = new Block("0");
                
                MerkleTree mt = MerkleTree.getInstance(eoTranx.getEngineOilTransaction());
        		mt.build();
        		String root = mt.getRoot();

        		eoTranx.setMerkleRoot(root);
        		System.out.println("Merkle Root = "+ root);
        		
        		Block blck = new Block(genesis.getHeader().getCurrentHash());
        		blck.setTransactions(eoTranx);
        		System.out.println(blck);
                
                // Add the Block to the blockchain
                blockchain.nextBlock(blck);
                
    			blockchain.distribute();
                
                showSuccessDialog(); // Display a success dialog

                System.out.println("Supplier assigned successfully.");
            } else {
            	showFailDialog(); // Display a success dialog
            	System.out.println("Assigning supplier failed.");
            }
             
            stage.close();
        }
    }
    
    private ProductInformation getProductInformation(String productCode) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(productInformationFilePath))) {
            String line;
            
            PredefinedCharsSecretKey secretKey = PredefinedCharsSecretKey.getInstance();
            Key preSecretKey = secretKey.getPreSecretKey();
            Symmetric symm = new Symmetric();
            
            while ((line = reader.readLine()) != null) {
            	String data = symm.decrypt(line, preSecretKey);
                if (data.startsWith("ProductInformation") && data.contains("code=" + productCode)) {
                    // Extract the required attributes from the line
                    String brand = extractAttributeValue(data, "brand=");
                    String name = extractAttributeValue(data, "name=");
                    String code = extractAttributeValue(data, "code=");
                    String specifications = extractAttributeValue(data, "specifications=");
                    String factory = extractAttributeValue(data, "factory=");
                    String manufacturingDate = extractAttributeValue(data, "manufacturingDate=");

                    // Create and return the ProductInformation object
                    return new ProductInformation(brand, name, code, specifications, factory, manufacturingDate);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // If the product information is not found, return null or handle the case as required
        return null;
    }

    private String extractAttributeValue(String line, String attribute) {
    	int startIndex = line.indexOf(attribute) + attribute.length();
    	int endIndex = line.indexOf(",", startIndex);
    	if (endIndex == -1) {
    	    endIndex = line.indexOf("]", startIndex);
    	    if (endIndex == -1) {
    	        endIndex = line.length();  // Use the end of the line if "]" is not found
    	    }
    	}
    	return line.substring(startIndex, endIndex);
    }


    private boolean generateDigitalSignature(String data, String dt) throws Exception {
    	String folderName = "SignedTransaction";
    	File folder = new File(folderName);
        if (!folder.exists()) {
            folder.mkdir();
        }
    	
    	MyKeyPair.create();
		byte[] publicKey = MyKeyPair.getPublicKey().getEncoded();
		byte[] privateKey = MyKeyPair.getPrivateKey().getEncoded();
		MyKeyPair.put(publicKey, "MyKeyPair/PublicKey");
		MyKeyPair.put(privateKey, "MyKeyPair/PrivateKey");
		
		DigitalSignature signature = new DigitalSignature();
        byte[] signedTransaction = signature.getSignature(data, MyKeyPair.getPrivateKey());
        String fileName = "signed_eo_transaction_" + dt.replace(":", "-") + ".txt";
        String filePath = folderName + "/" + fileName;
        
        Files.write(Paths.get(filePath), signedTransaction);
        
        System.out.println("Transaction signed successfully in " + filePath);
        return checkDigitalSignature(dt, signature);
    }
    
    private boolean checkDigitalSignature(String dt, DigitalSignature sig) throws Exception {
    	
    	String signedFilePath = "SignedTransaction/" + "signed_eo_transaction_" + dt.replace(":", "-") + ".txt";
    	String FilePath = "EngineOilTransaction/" + "eo_transaction_" + dt.replace(":", "-") + ".txt";
    	// Load the signed job application form from a file
        byte[] signedTransactionBytes = Files.readAllBytes(Paths.get(signedFilePath));
        String transaction = Files.readString(Paths.get(FilePath));
        
        // Verify the signature of the job application form
        boolean valid = sig.isTextAndSignatureValid(transaction, signedTransactionBytes, MyKeyPair.getPublicKey());
        if (valid) {
            System.out.println("Transaction Digital Signature Check Valid.");
        } else {
        	try {
                Files.deleteIfExists(Paths.get(FilePath));
                Files.deleteIfExists(Paths.get(signedFilePath));
                System.out.println("Transaction rejected due to invalid Digital Signature.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return valid;
    }

    private void writeTransactionFile(String transaction, String dt) {
        try {
            // Create the EngineOilTransaction folder if it doesn't exist
            String folderName = "EngineOilTransaction";
            File folder = new File(folderName);
            if (!folder.exists()) {
                folder.mkdir();
            }

            // Generate a unique file name based on the current timestamp
            String fileName = "eo_transaction_" + dt.replace(":", "-") + ".txt";
            String filePath = folderName + "/" + fileName;
            FileWriter writer = new FileWriter(filePath);
            
            // Write the transaction details to the file
            writer.write(transaction);
            writer.close();

            System.out.println("Transaction details saved successfully in " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void showSuccessDialog() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Assign Supplier");
        alert.setHeaderText(null);
        alert.setContentText("Supplier assigned successfully.");
        alert.showAndWait();
    }
    
    private void showFailDialog() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Assign Supplier");
        alert.setHeaderText(null);
        alert.setContentText("Supplier assigned failed.");
        alert.showAndWait();
    }

}
