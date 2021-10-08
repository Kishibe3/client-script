package com.clientScript.utils.ShapesRenderer;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.Sets;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.value.BlockValue;
import com.clientScript.value.EntityValue;
import com.clientScript.value.FormattedTextValue;
import com.clientScript.value.ListValue;
import com.clientScript.value.MapValue;
import com.clientScript.value.NumericValue;
import com.clientScript.value.StringValue;
import com.clientScript.value.Value;

public class ShapeDispatcher {
	public static Pair<ExpiringShape, String> fromFunctionArgs(List<Value> lv) {
		if (lv.size() < 3)
			throw new InternalExpressionException("'draw_shape' takes at least three parameters, shape name, duration, and its params");
		String shapeType = lv.get(0).getString();
		Value duration = NumericValue.asNumber(lv.get(1), "duration");
		Map<String, Value> params;

		if (lv.size() == 3) {
			Value paramValue = lv.get(2);
			if (paramValue instanceof MapValue) {
				params = new HashMap<>();
				((MapValue)paramValue).getMap().entrySet().forEach(e -> params.put(e.getKey().getString(), e.getValue()));
			}
			else if (paramValue instanceof ListValue)
				params = parseParams(((ListValue)paramValue).getItems());
			else
				throw new InternalExpressionException("Parameters for 'draw_shape' need to be defined either in a list or a map");
		}
		else {
			List<Value> paramList = new ArrayList<>();
			for (int i=2; i<lv.size(); i++)
				paramList.add(lv.get(i));
			params = parseParams(paramList);
		}
		MinecraftClient mcc = MinecraftClient.getInstance();
		params.putIfAbsent("dim", new StringValue(mcc.world.getRegistryKey().getValue().toString()));
		params.putIfAbsent("duration", duration);
		// TODO do we need "player" option?
		return Pair.of(create(shapeType, params), shapeType);
	}

	public static Map<String, Value> parseParams(List<Value> items) {
        // parses params from API function
        if (items.size() % 2 == 1)
			throw new InternalExpressionException("Shape parameters list needs to be of even size");
        Map<String, Value> param = new HashMap<>();
        int i = 0;
        while(i < items.size()) {
            String name = items.get(i).getString();
            Value val = items.get(i + 1);
            param.put(name, val);
            i += 2;
        }
        return param;
    }

	public static ExpiringShape create(String shapeType, Map<String, Value> userParams) {
		userParams.put("shape", new StringValue(shapeType));
		userParams.keySet().forEach(key -> {
			Param param = Param.of.get(key);
			if (param == null)
				throw new InternalExpressionException("Unknown feature for shape: " + key);
			userParams.put(key, param.validate(userParams, userParams.get(key)));
		});
		Function<Map<String, Value>, ExpiringShape> factory = ExpiringShape.shapeProviders.get(shapeType);
		if (factory == null)
			throw new InternalExpressionException("Unknown shape: " + shapeType);
		return factory.apply(userParams);
	}

	public abstract static class ExpiringShape {
		public static final Map<String, Function<Map<String, Value>, ExpiringShape>> shapeProviders = new HashMap<>() {{
			put("line", creator(Line::new));
            put("box", creator(Box::new));
            put("sphere", creator(Sphere::new));
            put("label", creator(DisplayedText::new));
		}};
		private static Function<Map<String, Value>, ExpiringShape> creator(Supplier<ExpiringShape> shapeFactory) {
			return o -> {
				ExpiringShape shape = shapeFactory.get();
				shape.fromOptions(o);
				return shape;
			};
		}

		// list of params that need to be there
		private final Set<String> required = ImmutableSet.of("shape", "duration", "dim");
        private final Map<String, Value> optional = ImmutableMap.of(
            "color", new NumericValue(-1),
            "follow", new NumericValue(-1),
            "fill", new NumericValue(0xffffff00),
			"snap", new StringValue("xyz")
        );

		private static final double xdif = new Random().nextDouble();
        private static final double ydif = new Random().nextDouble();
        private static final double zdif = new Random().nextDouble();
        int vec3dhash(Vec3d vec) {
            return vec.add(ExpiringShape.xdif, ExpiringShape.ydif, ExpiringShape.zdif).hashCode();
        }

		float lineWidth = 2.0F;
		protected float r, g, b, a;
		protected int color;
        protected float fr, fg, fb, fa;
		protected int fillColor;
		protected int duration = 0;
		protected int followEntity = -1;
        protected String snapTo;
		protected boolean snapX, snapY, snapZ;
        protected boolean discreteX, discreteY, discreteZ;
        protected RegistryKey<World> shapeDimension;
		private long key;

		protected ExpiringShape() {}

		public void fromOptions(Map<String, Value> options) {
			Set<String> required = requiredParams(), optional = optionalParams(), all = Sets.union(required, optional);
			if (!all.containsAll(options.keySet()))
				throw new InternalExpressionException("Received unexpected parameters for shape: " + Sets.difference(options.keySet(), all));
			if (!options.keySet().containsAll(required))
                throw new InternalExpressionException("Missing required parameters for shape: " + Sets.difference(required, options.keySet()));
            options.keySet().forEach(k -> {
                if (!canTake(k))
                    throw new InternalExpressionException("Parameter " + k + " doesn't apply for shape " + options.get("shape").getString());
            });
			init(options);
		}

		protected Set<String> requiredParams() {
			return this.required;
		}

		protected Set<String> optionalParams() {
			return this.optional.keySet();
		}

		private boolean canTake(String param) {
			return requiredParams().contains(param) || optionalParams().contains(param);
		}

		protected void init(Map<String, Value> options) {
			this.duration = NumericValue.asNumber(options.get("duration")).getInt();
			this.color = NumericValue.asNumber(options.getOrDefault("color", this.optional.get("color"))).getInt();
            this.r = (float)(this.color >> 24 & 0xFF) / 255.0F;
            this.g = (float)(this.color >> 16 & 0xFF) / 255.0F;
            this.b = (float)(this.color >>  8 & 0xFF) / 255.0F;
            this.a = (float)(this.color & 0xFF) / 255.0F;

			this.fillColor = NumericValue.asNumber(options.getOrDefault("fill", this.optional.get("fill"))).getInt();
			this.fr = (float)(this.fillColor >> 24 & 0xFF) / 255.0F;
            this.fg = (float)(this.fillColor >> 16 & 0xFF) / 255.0F;
            this.fb = (float)(this.fillColor >>  8 & 0xFF) / 255.0F;
            this.fa = (float)(this.fillColor & 0xFF) / 255.0F;

			this.shapeDimension = RegistryKey.of(Registry.WORLD_KEY, new Identifier(options.get("dim").getString()));
			if (options.containsKey("follow")) {
				this.followEntity = NumericValue.asNumber(options.getOrDefault("follow", this.optional.get("follow"))).getInt();
				this.snapTo = options.getOrDefault("snap", this.optional.get("snap")).getString().toLowerCase(Locale.ROOT);
                this.snapX = this.snapTo.contains("x");
                this.snapY = this.snapTo.contains("y");
                this.snapZ = this.snapTo.contains("z");
                this.discreteX = this.snapTo.contains("dx");
                this.discreteY = this.snapTo.contains("dy");
                this.discreteZ = this.snapTo.contains("dz");
			}
		}
		
		public int getExpiry() {
			return this.duration;
		}

		public Vec3d toAbsolute(Entity e, Vec3d vec, float partialTick) {
			return vec.add(
				this.snapX? (this.discreteX? MathHelper.floor(e.getX()) : MathHelper.lerp(partialTick, e.prevX, e.getX())) : 0.0,
                this.snapY? (this.discreteY? MathHelper.floor(e.getY()) : MathHelper.lerp(partialTick, e.prevY, e.getY())) : 0.0,
                this.snapZ? (this.discreteZ? MathHelper.floor(e.getZ()) : MathHelper.lerp(partialTick, e.prevZ, e.getZ())) : 0.0
			);
		}

		public Vec3d relativiseRender(Vec3d vec, float partialTick) {
			if (this.followEntity < 0)
				return vec;
			MinecraftClient mcc = MinecraftClient.getInstance();
			Entity e = mcc.world.getEntityById(this.followEntity);
			if (e == null)
				return vec;
			return toAbsolute(e, vec, partialTick);
		}

		public long key() {
			if (this.key != 0) return this.key;
			key = calcKey();
			return key;
		}

		protected long calcKey() {
			// using FNV-1a algorithm
            long hash = -3750763034362895579L;
            hash ^= this.shapeDimension.hashCode(); hash *= 1099511628211L;
            hash ^= this.color;                     hash *= 1099511628211L;
            hash ^= this.followEntity;              hash *= 1099511628211L;
            if (this.followEntity >= 0) {
                hash ^= this.snapTo.hashCode();     hash *= 1099511628211L;
            }
            hash ^= Float.hashCode(this.lineWidth); hash *= 1099511628211L;
            if (this.fa != 0.0) {
				hash = 31 * hash + this.fillColor;  hash *= 1099511628211L;
			}
            return hash;
        }

		public Vec3d vecFromValue(Value value) {
			if (!(value instanceof ListValue))
				throw new InternalExpressionException(value.getPrettyString() + " is not a triple");
			List<Value> elements = ((ListValue)value).getItems();
			return new Vec3d(
                NumericValue.asNumber(elements.get(0)).getDouble(),
                NumericValue.asNumber(elements.get(1)).getDouble(),
                NumericValue.asNumber(elements.get(2)).getDouble()
            );
		}
	}
	
	public static class DisplayedText extends ExpiringShape {
		private final Set<String> required = ImmutableSet.of("pos", "text");
        private final Map<String, Value> optional = ImmutableMap.<String, Value>builder().
            put("facing", new StringValue("player")).
            put("raise", new NumericValue(0)).
            put("tilt", new NumericValue(0)).
            put("lean", new NumericValue(0)).
            put("turn", new NumericValue(0)).
            put("indent", new NumericValue(0)).
            put("height", new NumericValue(0)).
            put("align", new StringValue("center")).
            put("size", new NumericValue(10)).
            put("value", Value.NULL).
            put("doublesided", new NumericValue(0)).
            build();

		Vec3d pos;
		String text;
        int textcolor;
        int textbck;

        Direction facing = null;
        float raise;
        float tilt;
        float lean;
        float turn;
        float size;
		float indent;
        int align = 0;
        float height;
        Text value;
        boolean doublesided = false;

		public DisplayedText() {}

		@Override
		protected void init(Map<String, Value> options) {
			super.init(options);
			this.pos = vecFromValue(options.get("pos"));
			this.value = ((FormattedTextValue)options.get("text")).getText();
			this.text = this.value.getString();
			if (options.containsKey("value"))
				this.value = ((FormattedTextValue)options.get("value")).getText();
			this.textcolor = rgba2argb(this.color);
			this.textbck = rgba2argb(this.fillColor);
			String dir = options.getOrDefault("facing", this.optional.get("facing")).getString();
			switch (dir) {
				case "north":
					this.facing = Direction.NORTH;
					break;
				case "south":
					this.facing = Direction.SOUTH;
					break;
				case "east":
					this.facing = Direction.EAST;
					break;
				case "west":
					this.facing = Direction.WEST;
					break;
				case "up":
					this.facing = Direction.UP;
					break;
				case "down":
					this.facing = Direction.DOWN;
					break;
			}
			if (options.containsKey("align")) {
				String alignStr = options.get("align").getString();
                if ("right".equalsIgnoreCase(alignStr))
                    this.align = 1;
                else if ("left".equalsIgnoreCase(alignStr))
                    this.align = -1;
			}
			if (options.containsKey("doublesided"))
                this.doublesided = options.get("doublesided").getBoolean();
			
			this.raise = NumericValue.asNumber(options.getOrDefault("raise", this.optional.get("raise"))).getFloat();
            this.tilt = NumericValue.asNumber(options.getOrDefault("tilt", this.optional.get("tilt"))).getFloat();
            this.lean = NumericValue.asNumber(options.getOrDefault("lean", this.optional.get("lean"))).getFloat();
            this.turn = NumericValue.asNumber(options.getOrDefault("turn", this.optional.get("turn"))).getFloat();
            this.indent = NumericValue.asNumber(options.getOrDefault("indent", this.optional.get("indent"))).getFloat();
            this.height = NumericValue.asNumber(options.getOrDefault("height", this.optional.get("height"))).getFloat();
            this.size = NumericValue.asNumber(options.getOrDefault("size", this.optional.get("size"))).getFloat();
		}

		@Override
        protected Set<String> requiredParams() {
			return Sets.union(super.requiredParams(), this.required);
		}

        @Override
        protected Set<String> optionalParams() {
			return Sets.union(super.optionalParams(), this.optional.keySet());
		}

		private int rgba2argb(int color) {
            int r = Math.max(1, color >> 24 & 0xFF);
            int g = Math.max(1, color >> 16 & 0xFF);
            int b = Math.max(1, color >>  8 & 0xFF);
            int a = color & 0xFF;
            return (a << 24) + (r << 16) + (g << 8) + b;
        }

		@Override
        public long calcKey() {
            long hash = super.calcKey();
            hash ^= 4;                                  hash *= 1099511628211L;
            hash ^= vec3dhash(this.pos);                hash *= 1099511628211L;
            hash ^= this.text.hashCode();               hash *= 1099511628211L;
            if (this.facing != null)
				hash ^= this.facing.hashCode();         hash *= 1099511628211L;
            hash ^= Float.hashCode(this.raise);         hash *= 1099511628211L;
            hash ^= Float.hashCode(this.tilt);          hash *= 1099511628211L;
            hash ^= Float.hashCode(this.lean);          hash *= 1099511628211L;
            hash ^= Float.hashCode(this.turn);          hash *= 1099511628211L;
            hash ^= Float.hashCode(this.indent);        hash *= 1099511628211L;
            hash ^= Float.hashCode(this.height);        hash *= 1099511628211L;
            hash ^= Float.hashCode(this.size);          hash *= 1099511628211L;
            hash ^= Integer.hashCode(this.align);       hash *= 1099511628211L;
            hash ^= Boolean.hashCode(this.doublesided); hash *= 1099511628211L;

            return hash;
        }
	}

	public static class Box extends ExpiringShape {
        private final Set<String> required = ImmutableSet.of("from", "to");
        private final Map<String, Value> optional = ImmutableMap.of();
        
		Vec3d from;
        Vec3d to;
		
		public Box() {}

		@Override
        protected void init(Map<String, Value> options) {
            super.init(options);
            this.from = vecFromValue(options.get("from"));
            this.to = vecFromValue(options.get("to"));
        }

		@Override
        protected Set<String> requiredParams() {
			return Sets.union(super.requiredParams(), this.required);
		}

        @Override
        protected Set<String> optionalParams() {
			return Sets.union(super.optionalParams(), this.optional.keySet());
		}

		@Override
        public long calcKey() {
            long hash = super.calcKey();
            hash ^= 1;                     hash *= 1099511628211L;
            hash ^= vec3dhash(this.from);  hash *= 1099511628211L;
            hash ^= vec3dhash(this.to);    hash *= 1099511628211L;
            return hash;
        }
	}

	public static class Line extends ExpiringShape {
		private final Set<String> required = ImmutableSet.of("from", "to");
        private final Map<String, Value> optional = ImmutableMap.of();

		Vec3d from;
        Vec3d to;

		private Line() {
			super();
		}

		@Override
		protected void init(Map<String, Value> options) {
			super.init(options);
			this.from = vecFromValue(options.get("from"));
            this.to = vecFromValue(options.get("to"));
		}

		@Override
		protected Set<String> requiredParams() {
			return Sets.union(super.requiredParams(), this.required);
		}

		@Override
		protected Set<String> optionalParams() {
			return Sets.union(super.optionalParams(), this.optional.keySet());
		}

		@Override
		public long calcKey() {
			long hash = super.calcKey();
			hash ^= 2;                     hash *= 1099511628211L;
            hash ^= vec3dhash(this.from);  hash *= 1099511628211L;
            hash ^= vec3dhash(this.to);    hash *= 1099511628211L;
            return hash;
		}
	}

	public static class Sphere extends ExpiringShape {
		private final Set<String> required = ImmutableSet.of("center", "radius");
        private final Map<String, Value> optional = ImmutableMap.of("level", Value.ZERO);

		Vec3d center;
        float radius;
        int subdivisions;

		private Sphere() {
            super();
        }

		@Override
		protected void init(Map<String, Value> options) {
			super.init(options);
			this.center = vecFromValue(options.get("center"));
            this.radius = NumericValue.asNumber(options.get("radius")).getFloat();
			this.subdivisions = NumericValue.asNumber(options.getOrDefault("level", this.optional.get("level"))).getInt();
			if (this.subdivisions <= 0)
				this.subdivisions = Math.max(10, (int)(10 * Math.sqrt(this.radius)));
		}

		@Override
		protected Set<String> requiredParams() {
			return Sets.union(super.requiredParams(), this.required);
		}

		@Override
		protected Set<String> optionalParams() {
			return Sets.union(super.optionalParams(), this.optional.keySet());
		}

		@Override
		public long calcKey() {
			long hash = super.calcKey();
			hash ^= 3;                            hash *= 1099511628211L;
            hash ^= vec3dhash(this.center);       hash *= 1099511628211L;
            hash ^= Double.hashCode(this.radius); hash *= 1099511628211L;
            hash ^= this.subdivisions;            hash *= 1099511628211L;
            return hash;
		}
	}

	public static abstract class Param {
		public static Map<String, Param> of = new HashMap<String, Param>() {{
			put("shape", new ShapeParam());
			put("duration", new NonNegativeIntParam("duration"));
			put("dim", new DimensionParam());
			put("color", new ColorParam("color"));
			put("follow", new EntityParam("follow"));
            put("fill", new ColorParam("fill"));
			put("snap", new StringChoiceParam("snap",
                "xyz", "xz", "yz", "xy", "x", "y", "z",
                "dxdydz", "dxdz", "dydz", "dxdy", "dx", "dy", "dz",
                "xdz", "dxz", "ydz", "dyz", "xdy", "dxy",
                "xydz", "xdyz", "xdydz", "dxyz", "dxydz", "dxdyz"
            ));

			put("from", new Vec3Param("from", false));
            put("to", new Vec3Param("to", true));
            put("center", new Vec3Param("center", false));
			put("radius", new PositiveFloatParam("radius"));
			put("level", new PositiveIntParam("level"));
            put("pos", new Vec3Param("pos", false));
            put("text", new FormattedTextParam("text"));
			put("facing", new StringChoiceParam("axis", "player", "north", "south", "east", "west", "up", "down"));
            put("raise", new FloatParam("raise"));
            put("tilt", new FloatParam("tilt"));
            put("lean", new FloatParam("tilt"));
            put("turn", new FloatParam("tilt"));
			put("indent", new FloatParam("indent"));
            put("height", new FloatParam("height"));
            put("align", new StringChoiceParam("align", "center", "left", "right"));
            put("size", new PositiveIntParam("size"));
			put("value", new FormattedTextParam("value"));
			put("doublesided", new BoolParam("doublesided"));
		}};
		protected String id;
		protected Param(String id) {
			this.id = id;
		}

		//validates value, returning null if not necessary to keep it and serialize
        public abstract Value validate(Map<String, Value> options, Value value);
	}

	public static abstract class StringParam extends Param {
		protected StringParam(String id) {
			super(id);
		}
	}

	public static abstract class NumericParam extends Param {
		protected NumericParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			if (!(value instanceof NumericValue))
				throw new InternalExpressionException("'" + this.id + "' needs to be a number");
			return value;
		}
	}

	public static class EntityParam extends Param {
		protected EntityParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			if (value instanceof EntityValue)
				return new NumericValue(((EntityValue)value).getEntity().getId());
			// TODO deleted the player part
			throw new InternalExpressionException(this.id + " parameter needs to represent an entity");
		}
	}

	public static class Vec3Param extends Param {
		private boolean roundsUpForBlocks;

		protected Vec3Param(String id, boolean doesRoundUpForBlocks) {
			super(id);
			this.roundsUpForBlocks = doesRoundUpForBlocks;
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			return validate(this, options, value, this.roundsUpForBlocks);
		}

		public static Value validate(Param p, Map<String, Value> options, Value value, boolean roundsUp) {
			if (value instanceof BlockValue) {
				if (options.containsKey("follow"))
					throw new InternalExpressionException(p.id + " parameter cannot use blocks as positions for relative positioning due to 'follow' attribute being present");
				BlockPos pos = ((BlockValue)value).getPos();
				int offset = roundsUp? 1 : 0;
				return ListValue.of(
					new NumericValue(pos.getX() + offset),
                    new NumericValue(pos.getY() + offset),
                    new NumericValue(pos.getZ() + offset)
				);
			}
			if (value instanceof ListValue) {
                List<Value> values = ((ListValue)value).getItems();
                if (values.size() != 3)
					throw new InternalExpressionException("'" + p.id + "' requires 3 numerical values");
                for (Value component: values) {
                    if (!(component instanceof NumericValue))
                        throw new InternalExpressionException("'" + p.id + "' requires 3 numerical values");
                }
                return value;
            }
			if (value instanceof EntityValue) {
                if (options.containsKey("follow"))
                    throw new InternalExpressionException(p.id+" parameter cannot use entity as positions for relative positioning due to 'follow' attribute being present");
                Entity e = ((EntityValue)value).getEntity();
                return ListValue.of(
                    new NumericValue(e.getX()),
                    new NumericValue(e.getY()),
                    new NumericValue(e.getZ())
                );
            }
			throw new InternalExpressionException("'" + p.id + "' requires a triple, block or entity to indicate position");
		}
	}

	public static class ShapeParam extends StringParam {
		protected ShapeParam() {
			super("shape");
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			String shape = value.getString();
			if (!ExpiringShape.shapeProviders.containsKey(shape))
				throw new InternalExpressionException("Unknown shape: " + shape);
			return value;
		}
	}

	public static class DimensionParam extends StringParam {
		protected DimensionParam() {
			super("dim");
		}

		@Override
        public Value validate(Map<String, Value> options, Value value) {
			return value;
		}
	}

	public static class StringChoiceParam extends StringParam {
		private Set<String> options;

        public StringChoiceParam(String id, String ... options) {
            super(id);
            this.options = Sets.newHashSet(options);
        }

        @Override
        public Value validate(Map<String, Value> options, Value value) {
            if (this.options.contains(value.getString()))
				return value;
            return null;
        }
	}

	public static class FormattedTextParam extends StringParam {
		protected FormattedTextParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			if (!(value instanceof FormattedTextValue))
				value = new FormattedTextValue(new LiteralText(value.getString()));
			return value;
		}
	}

	public static class NonNegativeIntParam extends NumericParam {
		protected NonNegativeIntParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			Value ret = super.validate(options, value);
			double retVal = ((NumericValue)ret).getDouble();
			if (retVal < 0)
				throw new InternalExpressionException("'" + this.id + "' should be non-negative");
			if (retVal != (int)retVal)
				throw new InternalExpressionException("'" + this.id + "' should be an integer");
			return ret;
		}
	}

	public static class ColorParam extends NumericParam {
		protected ColorParam(String id) {
			super(id);
		}
	}

	public static abstract class PositiveParam extends NumericParam {
		protected PositiveParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
            Value ret = super.validate(options, value);
            if (((NumericValue)ret).getDouble() <= 0)
				throw new InternalExpressionException("'" + this.id + "' should be positive");
            return ret;
        }
	}

	public static class FloatParam extends PositiveParam {
		protected FloatParam(String id) {
			super(id);
		}
	}

	public static class BoolParam extends PositiveParam {
		protected BoolParam(String id) {
			super(id);
		}
	}

	public static class PositiveFloatParam extends PositiveParam {
		protected PositiveFloatParam(String id) {
			super(id);
		}
	}

	public static class PositiveIntParam extends PositiveParam {
		protected PositiveIntParam(String id) {
			super(id);
		}

		@Override
		public Value validate(Map<String, Value> options, Value value) {
			Value ret = super.validate(options, value);
			double retVal = ((NumericValue)ret).getDouble();
			if (retVal != (int)retVal)
				throw new InternalExpressionException("'" + this.id + "' should be an integer");
			return ret;
		}
	}
}