/*
 * Copyright 2013 Moving Blocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.persistence.serializers;

import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedBytes;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.EngineEntityManager;
import org.terasology.entitySystem.EntityBuilder;
import org.terasology.entitySystem.EntityRef;
import org.terasology.entitySystem.MutableComponentContainer;
import org.terasology.entitySystem.metadata.ClassMetadata;
import org.terasology.entitySystem.metadata.ComponentLibrary;
import org.terasology.entitySystem.metadata.ComponentMetadata;
import org.terasology.entitySystem.metadata.FieldMetadata;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.protobuf.EntityData;

import java.util.Map;
import java.util.Set;

/**
 * @author Immortius
 */
public class NetworkEntitySerializer {
    private static final Logger logger = LoggerFactory.getLogger(NetworkEntitySerializer.class);

    private ComponentSerializeCheck componentSerializeCheck = ComponentSerializeCheck.NullCheck.create();
    private EngineEntityManager entityManager;
    private ComponentLibrary componentLibrary;
    private BiMap<Class<? extends Component>, Integer> idTable = ImmutableBiMap.<Class<? extends Component>, Integer>builder().build();

    public NetworkEntitySerializer(EngineEntityManager entityManager, ComponentLibrary componentLibrary) {
        this.entityManager = entityManager;
        this.componentLibrary = componentLibrary;
    }

    public void setComponentSerializeCheck(ComponentSerializeCheck componentSerializeCheck) {
        this.componentSerializeCheck = componentSerializeCheck;
    }

    public ComponentSerializeCheck getComponentSerializeCheck() {
        return componentSerializeCheck;
    }

    public ComponentLibrary getComponentLibrary() {
        return componentLibrary;
    }

    public Map<Class<? extends Component>, Integer> getIdMapping() {
        return ImmutableMap.copyOf(idTable);
    }

    public void setIdMapping(Map<Class<? extends Component>, Integer> componentIdMapping) {
        this.idTable = ImmutableBiMap.copyOf(componentIdMapping);
    }

    public EntityData.PackedEntity.Builder serialize(EntityRef entity, boolean deltaAgainstPrefab, FieldSerializeCheck<Component> fieldCheck) {
        Prefab prefab = entity.getParentPrefab();
        if (prefab != null && deltaAgainstPrefab) {
            return serializeEntityDelta(entity, prefab, fieldCheck);
        } else {
            return serializeEntityFull(entity, fieldCheck);
        }
    }

    private EntityData.PackedEntity.Builder serializeEntityFull(EntityRef entityRef, FieldSerializeCheck<Component> fieldCheck) {
        EntityData.PackedEntity.Builder entity = EntityData.PackedEntity.newBuilder();
        ByteString.Output fieldIds = ByteString.newOutput();
        ByteString.Output componentFieldCounts = ByteString.newOutput();
        for (Component component : entityRef.iterateComponents()) {
            if (!componentSerializeCheck.serialize(componentLibrary.getMetadata(component.getClass()))) {
                continue;
            }

            serializeComponentFull(component, false, fieldCheck, entity, fieldIds, componentFieldCounts);
        }
        entity.setFieldIds(fieldIds.toByteString());
        entity.setComponentFieldCounts(componentFieldCounts.toByteString());

        return entity;
    }

    private EntityData.PackedEntity.Builder serializeEntityDelta(EntityRef entityRef, Prefab prefab, FieldSerializeCheck<Component> fieldCheck) {
        EntityData.PackedEntity.Builder entity = EntityData.PackedEntity.newBuilder();
        entity.setParentPrefabUri(prefab.getName());
        Set<Class<? extends Component>> presentClasses = Sets.newHashSet();

        ByteString.Output fieldIds = ByteString.newOutput();
        ByteString.Output componentFieldCounts = ByteString.newOutput();
        for (Component component : entityRef.iterateComponents()) {
            if (!componentSerializeCheck.serialize(componentLibrary.getMetadata(component.getClass()))) {
                continue;
            }

            presentClasses.add(component.getClass());

            Component prefabComponent = prefab.getComponent(component.getClass());

            if (prefabComponent == null) {
                serializeComponentFull(component, false, fieldCheck, entity, fieldIds, componentFieldCounts);
            } else {
                serializeComponentDelta(prefabComponent, component, fieldCheck, entity, fieldIds, componentFieldCounts);
            }
        }
        entity.setFieldIds(fieldIds.toByteString());
        entity.setComponentFieldCounts(componentFieldCounts.toByteString());

        for (Component prefabComponent : prefab.iterateComponents()) {
            if (!presentClasses.contains(prefabComponent.getClass()) && componentSerializeCheck.serialize(componentLibrary.getMetadata(prefabComponent.getClass()))) {
                entity.addRemovedComponent(idTable.get(prefabComponent.getClass()));
            }
        }
        return entity;
    }

    private void serializeComponentDelta(Component oldComponent, Component newComponent, FieldSerializeCheck<Component> fieldCheck, EntityData.PackedEntity.Builder entityData, ByteString.Output entityFieldIds, ByteString.Output componentFieldCounts) {
        ClassMetadata<?> componentMetadata = componentLibrary.getMetadata(oldComponent.getClass());
        if (componentMetadata == null) {
            logger.error("Unregistered component type: {}", oldComponent.getClass());
            return;
        }

        byte fieldCount = 0;
        for (FieldMetadata field : componentMetadata.iterateFields()) {
            if (fieldCheck.shouldSerializeField(field, newComponent)) {
                Object oldValue = field.getValue(oldComponent);
                Object newValue = field.getValue(newComponent);
                if (!Objects.equal(oldValue, newValue)) {
                    EntityData.Value fieldValue = field.serializeValue(newValue);
                    if (fieldValue != null) {
                        entityFieldIds.write(field.getId());
                        entityData.addFieldValue(fieldValue);
                        fieldCount++;
                    } else {
                        logger.error("Exception serializing component type: {}, field: {} - returned null", componentMetadata, field);
                    }
                }
            }
        }

        if (fieldCount > 0) {
            entityData.addComponentId(idTable.get(newComponent.getClass()));
            componentFieldCounts.write(fieldCount);
        }
    }

    private void serializeComponentFull(Component component, boolean ignoreIfNoFields, FieldSerializeCheck<Component> fieldCheck, EntityData.PackedEntity.Builder entityData, ByteString.Output entityFieldIds, ByteString.Output componentFieldCounts) {
        ClassMetadata<?> componentMetadata = componentLibrary.getMetadata(component.getClass());
        if (componentMetadata == null) {
            logger.error("Unregistered component type: {}", component.getClass());
        }

        byte fieldCount = 0;
        for (FieldMetadata field : componentMetadata.iterateFields()) {
            if (fieldCheck.shouldSerializeField(field, component)) {
                EntityData.Value fieldValue = field.serialize(component);
                if (fieldValue != null) {

                    entityFieldIds.write(field.getId());

                    entityData.addFieldValue(fieldValue);
                    fieldCount++;
                }
            }
        }

        if (fieldCount != 0 || !ignoreIfNoFields) {
            entityData.addComponentId(idTable.get(component.getClass()));
            componentFieldCounts.write(fieldCount);
        }
    }

    public void deserializeOnto(MutableComponentContainer entity, EntityData.PackedEntity entityData) {
        deserializeOnto(entity, entityData, FieldSerializeCheck.NullCheck.<Component>newInstance());
    }

    public void deserializeOnto(MutableComponentContainer entity, EntityData.PackedEntity entityData, FieldSerializeCheck<Component> fieldCheck) {
        int fieldPos = 0;
        for (int componentIndex = 0; componentIndex < entityData.getComponentIdCount(); ++componentIndex) {
            Class<? extends Component> componentClass = idTable.inverse().get((Integer) entityData.getComponentId(componentIndex));
            ComponentMetadata<?> metadata = componentLibrary.getMetadata(componentClass);
            if (metadata == null) {
                logger.warn("Skipping unknown component {}", entityData.getComponentId(componentIndex));
                fieldPos += UnsignedBytes.toInt(entityData.getComponentFieldCounts().byteAt(componentIndex));
                continue;
            }
            if (!componentSerializeCheck.serialize(metadata)) {
                fieldPos += UnsignedBytes.toInt(entityData.getComponentFieldCounts().byteAt(componentIndex));
                continue;
            }

            Component component = entity.getComponent(metadata.getType());
            boolean createdNewComponent = false;
            if (component == null) {
                createdNewComponent = true;
                component = metadata.newInstance();
            }
            for (int fieldIndex = 0; fieldIndex < UnsignedBytes.toInt(entityData.getComponentFieldCounts().byteAt(componentIndex)); ++fieldIndex) {
                byte fieldId = entityData.getFieldIds().byteAt(fieldPos);
                FieldMetadata fieldMetadata = metadata.getFieldById(fieldId);
                if (fieldMetadata != null && fieldCheck.shouldDeserializeField(fieldMetadata)) {
                    logger.trace("Deserializing field {} of component {} as value {}", fieldMetadata, metadata, entityData.getFieldValue(fieldPos));
                    fieldMetadata.deserializeOnto(component, entityData.getFieldValue(fieldPos));
                }
                fieldPos++;
            }
            if (createdNewComponent) {
                entity.addComponent(component);
            } else {
                entity.saveComponent(component);
            }

        }

        for (int componentId : entityData.getRemovedComponentList()) {
            Class<? extends Component> componentClass = idTable.inverse().get(componentId);
            ComponentMetadata<?> metadata = componentLibrary.getMetadata(componentClass);
            if (componentSerializeCheck.serialize(metadata)) {
                entity.removeComponent(metadata.getType());
            }
        }
    }

    public EntityRef deserialize(EntityData.PackedEntity entityData) {
        EntityBuilder target;
        if (entityData.hasParentPrefabUri()) {
            target = entityManager.newBuilder(entityData.getParentPrefabUri());
        } else {
            target = entityManager.newBuilder();
        }
        deserializeOnto(target, entityData);
        if (entityData.hasId()) {
            return entityManager.createEntityWithId(entityData.getId(), target.iterateComponents());
        } else {
            return target.build();
        }
    }


    public EntityData.PackedEntity serialize(EntityRef entityRef, Set<Class<? extends Component>> added, Set<Class<? extends Component>> changed, Set<Class<? extends Component>> removed, FieldSerializeCheck<Component> fieldCheck) {
        EntityData.PackedEntity.Builder entity = EntityData.PackedEntity.newBuilder();

        ByteString.Output fieldIds = ByteString.newOutput();
        ByteString.Output componentFieldCounts = ByteString.newOutput();
        for (Class<? extends Component> componentType : added) {
            Component component = entityRef.getComponent(componentType);
            if (component == null) {
                logger.error("Non-existent component marked as added: {}", componentType);
            }
            serializeComponentFull(entityRef.getComponent(componentType), false, fieldCheck, entity, fieldIds, componentFieldCounts);
        }
        for (Class<? extends Component> componentType : changed) {
            Component comp = entityRef.getComponent(componentType);
            if (comp != null) {
                serializeComponentFull(comp, true, fieldCheck, entity, fieldIds, componentFieldCounts);
            } else {
                logger.error("Non-existent component marked as changed: {}", componentType);
            }
        }
        for (Class<? extends Component> componentType : removed) {
            entity.addRemovedComponent(idTable.get(componentType));
        }
        entity.setFieldIds(fieldIds.toByteString());
        entity.setComponentFieldCounts(componentFieldCounts.toByteString());
        if (entity.getFieldIds().isEmpty()) {
            return null;
        } else {
            return entity.build();
        }
    }
}
