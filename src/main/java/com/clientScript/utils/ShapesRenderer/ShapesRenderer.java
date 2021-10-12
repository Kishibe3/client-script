package com.clientScript.utils.ShapesRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.blaze3d.systems.RenderSystem;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

public class ShapesRenderer {
	private static final Map<String, Map<RegistryKey<World>, Long2ObjectOpenHashMap<RenderedShape<? extends ShapeDispatcher.ExpiringShape>>>> shapes = new HashMap<>();
	private static final Map<String, Map<RegistryKey<World>, Long2ObjectOpenHashMap<RenderedShape<? extends ShapeDispatcher.ExpiringShape>>>> labels = new HashMap<>();
	private static Map<String, Function<ShapeDispatcher.ExpiringShape, RenderedShape<? extends ShapeDispatcher.ExpiringShape >>> renderedShapes
    = new HashMap<String, Function<ShapeDispatcher.ExpiringShape, RenderedShape<? extends ShapeDispatcher.ExpiringShape>>>() {{
        put("line", RenderedLine::new);
        put("box", RenderedBox::new);
        put("sphere", RenderedSphere::new);
        put("label", RenderedText::new);
    }};

	public ShapesRenderer() {}
	
	public static String getServer() throws Throwable {
		MinecraftClient mcc = MinecraftClient.getInstance();
		if (mcc.getServer() != null)
			return mcc.getServer().getServerMotd();
		else if (mcc.getCurrentServerEntry() != null)
			return mcc.getCurrentServerEntry().label.getString();
		else
			throw new Throwable("Unknow Player Circumstance");
	}
	
	public void render(MatrixStack matrices, Camera camera, float partialTick) {
		MinecraftClient mcc = MinecraftClient.getInstance();
		String serverKey;
		try {
			serverKey = ShapesRenderer.getServer();
		} catch (Throwable e) {
			return;
		}
		RegistryKey<World> dimension = mcc.world.getRegistryKey();

        if (serverKey == null || dimension == null)
            return;
        if (ShapesRenderer.shapes.get(serverKey) == null && ShapesRenderer.labels.get(serverKey) == null)
            return;
		if (ShapesRenderer.shapes.get(serverKey) != null && ShapesRenderer.labels.get(serverKey) != null
         && (ShapesRenderer.shapes.get(serverKey).get(dimension) == null || ShapesRenderer.shapes.get(serverKey).get(dimension).isEmpty())
         && (ShapesRenderer.labels.get(serverKey).get(dimension) == null || ShapesRenderer.labels.get(serverKey).get(dimension).isEmpty())
        )
			return;
		RenderSystem.disableTexture();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthFunc(515);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        double cameraX = camera.getPos().x;
        double cameraY = camera.getPos().y;
        double cameraZ = camera.getPos().z;
        long currentTime = mcc.world.getTime();
        
        if (ShapesRenderer.shapes.get(serverKey) != null && ShapesRenderer.shapes.get(serverKey).size() != 0) {
        	synchronized (ShapesRenderer.shapes) {
        		ShapesRenderer.shapes.get(serverKey).get(dimension).long2ObjectEntrySet().removeIf(
               		entry -> entry.getValue().isExpired(currentTime)
                );
				MatrixStack matrixStack = RenderSystem.getModelViewStack();
                GL11.glDisable(GL11.GL_DEPTH_TEST);  // render through blocks
				matrixStack.push();
				matrixStack.method_34425(matrices.peek().getModel());
				RenderSystem.applyModelViewMatrix();

				// lines
				RenderSystem.lineWidth(0.5F);
				ShapesRenderer.shapes.get(serverKey).get(dimension).values().forEach(s -> {
					if (s.shouldRender(dimension))
						s.renderLines(matrices, tessellator, bufferBuilder, cameraX, cameraY, cameraZ, partialTick);
				});

				// faces
				RenderSystem.lineWidth(0.1F);
				ShapesRenderer.shapes.get(serverKey).get(dimension).values().forEach(s -> {
					if (s.shouldRender(dimension))
						s.renderFaces(tessellator, bufferBuilder, cameraX, cameraY, cameraZ, partialTick);
				});

				RenderSystem.lineWidth(1.0F);
				matrixStack.pop();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
				RenderSystem.applyModelViewMatrix();
        	}
        }
		if (ShapesRenderer.labels.get(serverKey) != null && ShapesRenderer.labels.get(serverKey).size() != 0) {
			synchronized (ShapesRenderer.labels) {
				ShapesRenderer.labels.get(serverKey).get(dimension).long2ObjectEntrySet().removeIf(
               		entry -> entry.getValue().isExpired(currentTime)
                );
				ShapesRenderer.labels.get(serverKey).get(dimension).values().forEach(s -> {
					if (s.shouldRender(dimension))
						s.renderLines(matrices, tessellator, bufferBuilder, cameraX, cameraY, cameraZ, partialTick);
				});
			}
		}
        
		RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();
	}
	
	public static void addShape(List<Pair<ShapeDispatcher.ExpiringShape, String>> shapes) {
        String serverKey;
		try {
			serverKey = ShapesRenderer.getServer();
		} catch (Throwable e) {
			return;
		}
		for (Pair<ShapeDispatcher.ExpiringShape, String> pair: shapes) {
			ShapeDispatcher.ExpiringShape shape = pair.getLeft();
            String shapeType = pair.getRight();
            Function<ShapeDispatcher.ExpiringShape, RenderedShape<? extends ShapeDispatcher.ExpiringShape >> shapeFactory;
            shapeFactory = ShapesRenderer.renderedShapes.get(shapeType);
            if (shapeFactory != null) {
                RenderedShape<?> rShape = shapeFactory.apply(shape);
                RegistryKey<World> dim = shape.shapeDimension;
                long key = rShape.key();
                Map<String, Map<RegistryKey<World>, Long2ObjectOpenHashMap<RenderedShape<? extends ShapeDispatcher.ExpiringShape>>>> container = rShape.stageDeux()? ShapesRenderer.labels : ShapesRenderer.shapes;
                synchronized (container) {
                    RenderedShape<?> existing = container.computeIfAbsent(serverKey, s -> new HashMap<>()).computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>()).get(key);
                    // promoting previous shape
                    if (existing != null)
                        existing.promoteWith(rShape);
                    else
                        container.get(serverKey).get(dim).put(key, rShape);
                }
            }
		}
	}

	public abstract static class RenderedShape<T extends ShapeDispatcher.ExpiringShape> {
		protected T shape;
		long expiryTick;
		double renderEpsilon = 0;
		
		protected RenderedShape(T shape) {
            this.shape = shape;
			MinecraftClient mcc = MinecraftClient.getInstance();
			this.expiryTick = mcc.world.getTime() + shape.getExpiry();
            this.renderEpsilon = (3 + ((double)shape.key()) / Long.MAX_VALUE) / 1000;
		}
		
		public boolean isExpired(long currentTick) {
			return this.expiryTick < currentTick;
		}

		public boolean shouldRender(RegistryKey<World> dim) {
			if (this.shape.followEntity <= 0)
				return true;
			MinecraftClient mcc = MinecraftClient.getInstance();
			if (mcc.world == null)
				return false;
			/* TODO is it correct to delete?
			if (mcc.world.getRegistryKey() != dim)
				return false;
			*/
			return mcc.world.getEntityById(this.shape.followEntity) != null;
		}

        public boolean stageDeux() {
            return false;
        }

        public long key() {
            return this.shape.key();
        }

        public void promoteWith(RenderedShape<?> rShape) {
            this.expiryTick = rShape.expiryTick;
        }

		public abstract void renderLines(MatrixStack matrices, Tessellator tessellator, BufferBuilder builder, double cx, double cy, double cz, float partialTick);
		public void renderFaces(Tessellator tessellator, BufferBuilder builder, double cx, double cy, double cz, float partialTick) {}
	}

	public static class RenderedText extends RenderedShape<ShapeDispatcher.DisplayedText> {
		protected RenderedText(ShapeDispatcher.ExpiringShape shape) {
            super((ShapeDispatcher.DisplayedText)shape);
        }

		@Override
		public void renderLines(MatrixStack matrices, Tessellator tessellator, BufferBuilder builder, double cx, double cy, double cz, float partialTick) {
			if (this.shape.a == 0.0)
				return;
			if (this.shape.doublesided)
                RenderSystem.disableCull();
            else
                RenderSystem.enableCull();
			matrices.push();
			Vec3d v1 = this.shape.relativiseRender(this.shape.pos, partialTick);
			matrices.translate(v1.x - cx, v1.y - cy, v1.z - cz);
			MinecraftClient mcc = MinecraftClient.getInstance();
			if (this.shape.facing == null)
				matrices.multiply(mcc.gameRenderer.getCamera().getRotation());
			else {
				switch (this.shape.facing) {
					case NORTH:
                        break;
                    case SOUTH:
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180));
                        break;
                    case EAST:
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(270));
                        break;
                    case WEST:
                        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(90));
                        break;
                    case UP:
                        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(90));
                        break;
                    case DOWN:
                        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90));
                        break;
				}
			}
			matrices.scale(this.shape.size * 0.0025f, -this.shape.size * 0.0025f, this.shape.size * 0.0025f);
			if (this.shape.tilt != 0.0f)
				matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(this.shape.tilt));
            if (this.shape.lean != 0.0f)
				matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(this.shape.lean));
            if (this.shape.turn != 0.0f)
				matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(this.shape.turn));
            matrices.translate(-10 * this.shape.indent, -10 * this.shape.height - 9,  -10 * this.renderEpsilon - 10 * this.shape.raise);
			matrices.scale(-1, 1, 1);

			float text_x = 0;
			TextRenderer textRenderer = mcc.textRenderer;
			if (this.shape.align == 0)
				text_x = (float)(-textRenderer.getWidth(this.shape.value.getString())) / 2.0F;
			else if (this.shape.align == 1)
				text_x = (float)-textRenderer.getWidth(this.shape.value.getString());
			VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(builder);
			textRenderer.draw(this.shape.value, text_x, 0.0F, this.shape.textcolor, false, matrices.peek().getModel(), immediate, false, this.shape.textbck, 15728880);
			immediate.draw();
            matrices.pop();
            RenderSystem.enableCull();
		}

        @Override
        public void promoteWith(RenderedShape<?> rShape) {
            super.promoteWith(rShape);
            try {
                this.shape.value = ((ShapeDispatcher.DisplayedText)rShape.shape).value;
            }
            catch (ClassCastException ignored) {
                ignored.printStackTrace();
            }
        }

        @Override
        public boolean stageDeux() {
            return true;
        }
	}

	public static class RenderedBox extends RenderedShape<ShapeDispatcher.Box> {
		private RenderedBox(ShapeDispatcher.ExpiringShape shape) {
			super((ShapeDispatcher.Box)shape);
		}

		@Override
		public void renderLines(MatrixStack matrices, Tessellator tessellator, BufferBuilder bufferBuilder, double cx, double cy, double cz, float partialTick) {
			if (this.shape.a == 0.0)
				return;
			Vec3d v1 = this.shape.relativiseRender(this.shape.from, partialTick);
            Vec3d v2 = this.shape.relativiseRender(this.shape.to, partialTick);
			drawBoxWireGLLines(tessellator, bufferBuilder,
                (float)(v1.x - cx - this.renderEpsilon), (float)(v1.y - cy - this.renderEpsilon), (float)(v1.z - cz - this.renderEpsilon),
                (float)(v2.x - cx + this.renderEpsilon), (float)(v2.y - cy + this.renderEpsilon), (float)(v2.z - cz + this.renderEpsilon),
                v1.x != v2.x, v1.y != v2.y, v1.z != v2.z,
                this.shape.r, this.shape.g, this.shape.b, this.shape.a, this.shape.r, this.shape.g, this.shape.b
            );
		}

		@Override
		public void renderFaces(Tessellator tessellator, BufferBuilder bufferBuilder, double cx, double cy, double cz, float partialTick) {
			if (this.shape.a == 0.0)
				return;
			Vec3d v1 = this.shape.relativiseRender(this.shape.from, partialTick);
            Vec3d v2 = this.shape.relativiseRender(this.shape.to, partialTick);
			drawBoxFaces(tessellator, bufferBuilder,
                (float)(v1.x - cx - this.renderEpsilon), (float)(v1.y - cy - this.renderEpsilon), (float)(v1.z - cz - this.renderEpsilon),
                (float)(v2.x - cx + this.renderEpsilon), (float)(v2.y - cy + this.renderEpsilon), (float)(v2.z - cz + this.renderEpsilon),
                v1.x != v2.x, v1.y != v2.y, v1.z != v2.z,
                this.shape.fr, this.shape.fg, this.shape.fb, this.shape.fa
            );
		}
	}

	public static class RenderedLine extends RenderedShape<ShapeDispatcher.Line> {
		public RenderedLine(ShapeDispatcher.ExpiringShape shape) {
            super((ShapeDispatcher.Line)shape);
        }

        @Override
        public void renderLines(MatrixStack matrices, Tessellator tessellator, BufferBuilder bufferBuilder, double cx, double cy, double cz, float partialTick) {
            Vec3d v1 = this.shape.relativiseRender(this.shape.from, partialTick);
            Vec3d v2 = this.shape.relativiseRender(this.shape.to, partialTick);
			drawLine(tessellator, bufferBuilder,
                (float)(v1.x - cx - this.renderEpsilon), (float)(v1.y - cy - this.renderEpsilon), (float)(v1.z - cz - this.renderEpsilon),
                (float)(v2.x - cx + this.renderEpsilon), (float)(v2.y - cy + this.renderEpsilon), (float)(v2.z - cz + this.renderEpsilon),
                this.shape.r, this.shape.g, this.shape.b, this.shape.a
            );
        }
	}

	public static class RenderedSphere extends RenderedShape<ShapeDispatcher.Sphere> {
        public RenderedSphere(ShapeDispatcher.ExpiringShape shape) {
            super((ShapeDispatcher.Sphere)shape);
        }

        @Override
        public void renderLines(MatrixStack matrices, Tessellator tessellator, BufferBuilder bufferBuilder, double cx, double cy, double cz, float partialTick) {
            if (this.shape.a == 0.0)
				return;
            Vec3d vc = this.shape.relativiseRender(this.shape.center, partialTick);
			drawSphereWireframe(tessellator, bufferBuilder,
                (float)(vc.x - cx), (float)(vc.y - cy), (float)(vc.z - cz),
                (float)(this.shape.radius + this.renderEpsilon), this.shape.subdivisions,
                this.shape.r, this.shape.g, this.shape.b, this.shape.a
			);
        }

        @Override
        public void renderFaces(Tessellator tessellator, BufferBuilder bufferBuilder, double cx, double cy, double cz, float partialTick) {
            if (this.shape.fa == 0.0)
				return;
            Vec3d vc = this.shape.relativiseRender(this.shape.center, partialTick);
			drawSphereFaces(tessellator, bufferBuilder,
                (float)(vc.x - cx), (float)(vc.y - cy), (float)(vc.z - cz),
                (float)(this.shape.radius + this.renderEpsilon), this.shape.subdivisions,
                this.shape.fr, this.shape.fg, this.shape.fb, this.shape.fa
			);
        }
    }

	public static void drawBoxWireGLLines(
        Tessellator tessellator, BufferBuilder builder,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        boolean xthick, boolean ythick, boolean zthick,
        float red1, float grn1, float blu1, float alpha, float red2, float grn2, float blu2
	) {
		builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        if (xthick) {
            builder.vertex(x1, y1, z1).color(red1, grn2, blu2, alpha).next();
            builder.vertex(x2, y1, z1).color(red1, grn2, blu2, alpha).next();

            builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y2, z1).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x1, y1, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
        }
        if (ythick) {
            builder.vertex(x1, y1, z1).color(red2, grn1, blu2, alpha).next();
            builder.vertex(x1, y2, z1).color(red2, grn1, blu2, alpha).next();

            builder.vertex(x2, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y1, z2).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
        }
        if (zthick) {
            builder.vertex(x1, y1, z1).color(red2, grn2, blu1, alpha).next();
            builder.vertex(x1, y1, z2).color(red2, grn2, blu1, alpha).next();

            builder.vertex(x1, y2, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y1, z1).color(red1, grn1, blu1, alpha).next();

            builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
        }
        tessellator.draw();
	}

	public static void drawBoxFaces(
        Tessellator tessellator, BufferBuilder builder,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        boolean xthick, boolean ythick, boolean zthick,
        float red1, float grn1, float blu1, float alpha
	) {
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        if (xthick && ythick) {
            builder.vertex(x1, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y2, z1).color(red1, grn1, blu1, alpha).next();
            if (zthick) {
                builder.vertex(x1, y1, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();
            }
        }
        if (zthick && ythick) {
            builder.vertex(x1, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y2, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y1, z2).color(red1, grn1, blu1, alpha).next();
            if (xthick) {
                builder.vertex(x2, y1, z1).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();
            }
        }
        // now at least drawing one
        if (zthick && xthick) {
            builder.vertex(x1, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y1, z1).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x2, y1, z2).color(red1, grn1, blu1, alpha).next();
            builder.vertex(x1, y1, z2).color(red1, grn1, blu1, alpha).next();
			if (ythick) {
                builder.vertex(x1, y2, z1).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y2, z1).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
                builder.vertex(x1, y2, z2).color(red1, grn1, blu1, alpha).next();
            }
        }
        tessellator.draw();
    }

	public static void drawLine(Tessellator tessellator, BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2, float red1, float grn1, float blu1, float alpha) {
        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        builder.vertex(x1, y1, z1).color(red1, grn1, blu1, alpha).next();
        builder.vertex(x2, y2, z2).color(red1, grn1, blu1, alpha).next();
        tessellator.draw();
    }

	public static void drawSphereWireframe(
		Tessellator tessellator, BufferBuilder builder,
        float cx, float cy, float cz,
        float r, int subd,
        float red, float grn, float blu, float alpha
	) {
        float step = (float)Math.PI / (subd / 2);
        int num_steps180 = (int)(Math.PI / step) + 1;
        int num_steps360 = (int)(2 * Math.PI / step) + 1;
        for (int i=0; i<=num_steps360; i++) {
            builder.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            float theta = step * i ;
            for (int j=0; j<=num_steps180; j++) {
                float phi = step * j ;
                float x = r * MathHelper.sin(phi) * MathHelper.cos(theta);
                float z = r * MathHelper.sin(phi) * MathHelper.sin(theta);
                float y = r * MathHelper.cos(phi);
                builder.vertex(x + cx, y + cy, z + cz).color(red, grn, blu, alpha).next();
            }
            tessellator.draw();
        }
        for (int j= 0; j<=num_steps180; j++) {
            builder.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR); // line loop to line strip
            float phi = step * j ;
            for (int i=0; i<=num_steps360; i++) {
                float theta = step * i;
                float x = r * MathHelper.sin(phi) * MathHelper.cos(theta);
                float z = r * MathHelper.sin(phi) * MathHelper.sin(theta);
                float y = r * MathHelper.cos(phi);
                builder.vertex(x + cx, y + cy, z + cz).color(red, grn, blu, alpha).next();
            }
            tessellator.draw();
        }
    }

    public static void drawSphereFaces(
		Tessellator tessellator, BufferBuilder builder,
        float cx, float cy, float cz,
        float r, int subd,
        float red, float grn, float blu, float alpha
	) {
        float step = (float)Math.PI / (subd / 2);
        int num_steps180 = (int)(Math.PI / step) + 1;
        int num_steps360 = (int)(2 * Math.PI / step);
        for (int i=0; i<=num_steps360; i++) {
            float theta = i * step;
            float thetaprime = theta + step;
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);  // quad strip to quads
            float xb = 0;
            float zb = 0;
            float xbp = 0;
            float zbp = 0;
            float yp = r;
            for (int j=0; j<=num_steps180; j++) {
                float phi = j * step;
                float x = r * MathHelper.sin(phi) * MathHelper.cos(theta);
                float z = r * MathHelper.sin(phi) * MathHelper.sin(theta);
                float y = r * MathHelper.cos(phi);
                float xp = r * MathHelper.sin(phi) * MathHelper.cos(thetaprime);
                float zp = r * MathHelper.sin(phi) * MathHelper.sin(thetaprime);
                builder.vertex(xb + cx, yp + cy, zb + cz).color(red, grn, blu, alpha).next();
                builder.vertex(xbp + cx, yp + cy, zbp + cz).color(red, grn, blu, alpha).next();
                builder.vertex(xp + cx, y + cy, zp + cz).color(red, grn, blu, alpha).next();
                builder.vertex(x + cx, y + cy, z + cz).color(red, grn, blu, alpha).next();
                xb = x;
                zb = z;
                xbp = xp;
                zbp = zp;
                yp = y;
            }
            tessellator.draw();
        }
    }
}
