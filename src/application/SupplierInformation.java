package application;

import java.io.Serializable;

public class SupplierInformation implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String name;
	private String address;
	
	public SupplierInformation(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

	@Override
	public String toString() {
		return "SupplierInformation [name=" + name + ", address=" + address + "]";
	}
}
