package com.ore.infinium.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.ore.infinium.Mappers;
import com.ore.infinium.World;
import com.ore.infinium.components.ItemComponent;
import com.ore.infinium.components.SpriteComponent;
import com.ore.infinium.components.TagComponent;

/**
 * ***************************************************************************
 * Copyright (C) 2015 by Shaun Reich <sreich@kde.org>                    *
 * *
 * This program is free software; you can redistribute it and/or            *
 * modify it under the terms of the GNU General Public License as           *
 * published by the Free Software Foundation; either version 2 of           *
 * the License, or (at your option) any later version.                      *
 * *
 * This program is distributed in the hope that it will be useful,          *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of           *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
 * GNU General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU General Public License        *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.    *
 * ***************************************************************************
 */
public class SpriteRenderSystem extends EntitySystem {
    private World m_world;
    private SpriteBatch m_batch;
    //   public TextureAtlas m_atlas;

    public static int spriteCount;

    public SpriteRenderSystem(World world) {
        m_world = world;
    }

    public void addedToEngine(Engine engine) {
        m_batch = new SpriteBatch();
//        m_atlas = new TextureAtlas(Gdx.files.internal("packed/entities.atlas"));
    }

    public void removedFromEngine(Engine engine) {
        m_batch.dispose();
    }

    public void update(float delta) {

//        m_batch.setProjectionMatrix(m_world.m_camera.combined);
        m_batch.setProjectionMatrix(m_world.m_camera.combined);
        m_batch.begin();

        renderEntities(delta);
        renderDroppedEntities(delta);

        m_batch.end();
    }

    //fixme probably also droppedblocks?
    private void renderDroppedEntities(float delta) {
        //fixme obviously this is very inefficient...
        ImmutableArray<Entity> entities = m_world.engine.getEntitiesFor(Family.all(SpriteComponent.class).get());

        ItemComponent itemComponent;
        for (int i = 0; i < entities.size(); ++i) {
            itemComponent = Mappers.item.get(entities.get(i));
            //don't draw in-inventory or dropped items
            if (itemComponent == null || itemComponent.state != ItemComponent.State.DroppedInWorld) {
                continue;
            }

            SpriteComponent spriteComponent = Mappers.sprite.get(entities.get(i));

            m_batch.draw(spriteComponent.sprite, spriteComponent.sprite.getX() - (spriteComponent.sprite.getWidth() * 0.5f),
                    spriteComponent.sprite.getY() - (spriteComponent.sprite.getHeight() * 0.5f),
                    spriteComponent.sprite.getWidth(), spriteComponent.sprite.getHeight());
        }
    }

    private void renderEntities(float delta) {
        //todo need to exclude blocks?
        ImmutableArray<Entity> entities = m_world.engine.getEntitiesFor(Family.all(SpriteComponent.class).get());

        ItemComponent itemComponent;
        for (int i = 0; i < entities.size(); ++i) {
            itemComponent = Mappers.item.get(entities.get(i));
            //don't draw in-inventory or dropped items
            if (itemComponent != null && itemComponent.state != ItemComponent.State.InWorldState) {
                continue;
            }

            SpriteComponent spriteComponent = Mappers.sprite.get(entities.get(i));

            boolean placementGhost = false;

            TagComponent tagComponent = Mappers.tag.get(entities.get(i));
            if (tagComponent != null && tagComponent.tag.equals("itemPlacementGhost")) {
                placementGhost = true;
                if (spriteComponent.placementValid) {
                    m_batch.setColor(0, 1, 0, 0.6f);
                } else {
                    m_batch.setColor(1, 0, 0, 0.6f);
                }
            }

            m_batch.draw(spriteComponent.sprite, spriteComponent.sprite.getX() - (spriteComponent.sprite.getWidth() * 0.5f),
                    spriteComponent.sprite.getY() - (spriteComponent.sprite.getHeight() * 0.5f),
                    spriteComponent.sprite.getWidth(), spriteComponent.sprite.getHeight());

            if (placementGhost) {
                m_batch.setColor(1, 1, 1, 1);
            }
        }
    }
}