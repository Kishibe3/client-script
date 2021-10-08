package com.clientScript.value;

import net.minecraft.entity.Entity;

public class EntityValue extends Value {
    private Entity entity;

    public EntityValue(Entity e) {
        this.entity = e;
    }

    public Entity getEntity() {
        return this.entity;
    }

    @Override
    public String getString() {
        return getEntity().getName().getString();
    }
    
    @Override
    public boolean getBoolean() {
        return true;
    }
}
