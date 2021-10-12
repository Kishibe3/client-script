package com.clientScript.value;

import com.mojang.brigadier.StringReader;

import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.predicate.NbtPredicate;

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

    public NbtElement getData(String strPath) throws Exception {
        return NbtPathArgumentType.nbtPath().parse(new StringReader(strPath)).get(NbtPredicate.entityToNbt(this.entity)).get(0);
    }
}
