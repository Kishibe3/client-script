package com.clientScript.value;

public class LContainerValue extends FrameworkValue {
    public static final LContainerValue NULL_CONTAINER = new LContainerValue(null, null);

    private ContainerValueInterface container;
    private Value address;

    public LContainerValue(ContainerValueInterface c, Value v) {
        this.container = c;
        this.address = v;
    }

    public ContainerValueInterface getContainer() {
        return this.container;
    }

    public Value getAddress() {
        return this.address;
    }
}
