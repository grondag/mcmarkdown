package grondag.mcmd;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;

import grondag.fonthack.TextRendererExt;
import grondag.fonthack.TrueTypeGlyphExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

public class McMdRenderer {
	//	char ESC = '§';
	//
	public static final char BOLD = (char) 0xE000;
	public static final char STRIKETHROUGH = (char) 0xE001;
	public static final char UNDERLINE = (char) 0xE002;
	public static final char ITALIC = (char) 0xE003;
	public static final char INDENT_PLUS = (char) 0xE004;

	public static final char BOLD_OFF = (char) 0xE005;
	public static final char STRIKETHROUGH_OFF = (char) 0xE006;
	public static final char UNDERLINE_OFF = (char) 0xE007;
	public static final char ITALIC_OFF = (char) 0xE008;
	public static final char INDENT_MINUS = (char) 0xE009;

	public static final char NEWLINE  = (char) 0xE00A	;
	public static final char NEWLINE_PLUS_HALF  = (char) 0xE00B;

	/** No closing tag - resets x coordinate to current indentation level. */
	public static final char ALIGN_TO_INDENT = (char) 0xE00C;

	public static final char NOTHING = (char) 0xE00D;


	public static final String STR_BOLD = Character.toString(BOLD);
	public static final String STR_STRIKETHROUGH = Character.toString(STRIKETHROUGH);
	public static final String STR_UNDERLINE = Character.toString(UNDERLINE);
	public static final String STR_ITALIC = Character.toString(ITALIC);
	public static final String STR_INDENT_PLUS = Character.toString(INDENT_PLUS);

	public static final String STR_BOLD_OFF = Character.toString(BOLD_OFF);
	public static final String STR_STRIKETHROUGH_OFF = Character.toString(STRIKETHROUGH_OFF);
	public static final String STR_UNDERLINE_OFF = Character.toString(UNDERLINE_OFF);
	public static final String STR_ITALIC_OFF = Character.toString(ITALIC_OFF);
	public static final String STR_INDENT_MINUS = Character.toString(INDENT_MINUS);

	public static final String STR_ALIGN_TO_INDENT = Character.toString(ALIGN_TO_INDENT);
	public static final String STR_NEWLINE = Character.toString(NEWLINE);
	public static final String STR_HALF_NEWLINE = Character.toString(NEWLINE_PLUS_HALF);

	private final TextRenderer baseRenderer;
	private final TextRenderer italicRenderer;
	private final TextRenderer boldRenderer;
	private final TextRenderer boldItalicRenderer;

	private final TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();

	private final FontAdapter adapter;
	private final List<Rectangle> rects = Lists.newArrayList();

	final McMdStyle style;

	public McMdRenderer(
		McMdStyle style,
		TextRenderer baseRenderer,
		TextRenderer italicRenderer,
		TextRenderer boldRenderer,
		TextRenderer boldItalicRenderer)
	{
		this.style = style;
		this.baseRenderer = baseRenderer;
		this.italicRenderer = italicRenderer;
		this.boldRenderer = boldRenderer;
		this.boldItalicRenderer = boldItalicRenderer;

		//		final TextRenderer tex = MinecraftClient.getInstance().textRenderer;
		//		final FontStorage fs = ((TextRendererExt) tex).ext_fontStorage();
		//		final FontStorage ttf = ((TextRendererExt) baseRenderer).ext_fontStorage();
		//
		//		String out = "\"";
		//		int outCount = 0;
		//		for(char c = 0; c < '\uffff'; ++c) {
		//			final String s = Character.toString(c);
		//			final Glyph g = fs.getGlyph(c);
		//			if(g == BlankGlyph.INSTANCE  || s.equals(" ") || s.isEmpty()) {
		//				final Glyph tg = ttf.getGlyph(c);
		//				if (tg != null && tg != BlankGlyph.INSTANCE) {
		//					out = out + "\\u" + Integer.toHexString(c);
		//					if(++outCount == 16) {
		//						System.out.println(out + "\",");
		//						out = "\"";
		//						outCount = 0;
		//					}
		//				}
		//			}
		//		}
		//
		//		if(outCount > 0) {
		//			System.out.println(out + "\",");
		//		}


		if (((TextRendererExt) baseRenderer).ext_fontStorage().getGlyph('a') instanceof TrueTypeGlyphExt
			&& italicRenderer != null
			&& ((TextRendererExt) italicRenderer).ext_fontStorage().getGlyph('a') instanceof TrueTypeGlyphExt
			&& boldRenderer != null
			&& ((TextRendererExt) boldRenderer).ext_fontStorage().getGlyph('a') instanceof TrueTypeGlyphExt
			&& boldItalicRenderer != null
			&& ((TextRendererExt) boldItalicRenderer).ext_fontStorage().getGlyph('a') instanceof TrueTypeGlyphExt)
		{
			adapter = new TrueTypeAdapter();
		} else  {
			adapter = new StandardAdapter();
		}
	}

	class LineBreaker {
		int indent = 0;
		float margin = 0;
		int bold = 0;
		int italic = 0;
		final float indentWidth = adapter.indentWidth;
		final float lineHeight = style.lineHeight;
		final int width;

		LineBreaker(int width) {
			this.width = Math.max(1, width);
		}

		int breakLine(String text) {
			final int len = text.length();
			float w = 0.0F;
			int i = 0;
			int lastSpace = -1;

			char kernChar = NOTHING;

			for(boolean singleOrEmpty = true; i < len; ++i) {
				final char c = text.charAt(i);

				switch(c) {
				case BOLD:
					if(bold++ == 0) {
						kernChar = NOTHING;
					}
					break;

				case BOLD_OFF:
					if(--bold == 0) {
						kernChar = NOTHING;
					}
					break;

				case ITALIC:
					if(italic++ == 0) {
						kernChar = NOTHING;
					}
					break;

				case ITALIC_OFF:
					if(--italic == 0) {
						kernChar = NOTHING;
					}
					break;

				case INDENT_PLUS:
					margin = ++indent * indentWidth;
					break;

				case INDENT_MINUS:
					margin = --indent * indentWidth;
					break;

				case ALIGN_TO_INDENT:
					//because margin is always added, resetting to margin is resetting to zero
					kernChar = NOTHING;
					w = 0;
					break;

				case NEWLINE:
				case NEWLINE_PLUS_HALF:
					return i + 1;

				case ' ':
					lastSpace = i;
					kernChar = NOTHING;
					break;

				default:
					break;
				}

				if (w != 0.0F) {
					singleOrEmpty = false;
				}

				w += (kernChar == NOTHING ? adapter.getCharWidth(c, bold > 0, italic > 0, lineHeight) : adapter.getCharWidth(c, kernChar, bold > 0, italic > 0, lineHeight));

				if (bold > 0) {
					++w;
				}

				if (w + margin > width) {
					if (singleOrEmpty) {
						++i;
					}
					break;
				}
			}

			return i != len && lastSpace != -1 && lastSpace < i ? lastSpace : i;
		}
	}

	public List<String> wrapMarkdownToWidth(String markdown, int width, @Nullable List<String> lines) {
		if (lines == null)  {
			lines = new ArrayList<>();
		}

		String line;

		final LineBreaker breaker = new LineBreaker(width);

		for(; !markdown.isEmpty(); lines.add(line)) {
			final int lineWidth = breaker.breakLine(markdown);

			if (markdown.length() <= lineWidth) {
				lines.add(markdown);
				break;
			}

			line = markdown.substring(0, lineWidth);
			final char endChar = markdown.charAt(lineWidth);
			final boolean whitespaceEnd = endChar == ' ';
			markdown = markdown.substring(lineWidth + (whitespaceEnd ? 1 : 0));
		}

		return lines;
	}

	abstract class FontAdapter {
		final Tessellator tess = Tessellator.getInstance();
		final float indentWidth = ((TextRendererExt) baseRenderer).ext_fontStorage().getGlyph(' ').getAdvance() * 5;

		abstract float draw(char c, char kernChar, boolean bold, boolean italic, float x, float y, float height, BufferBuilder buffer, float red, float green, float blue, float alpha);

		protected abstract float getCharWidth(char kernChar, char c, boolean bold, boolean italic, float height);

		protected abstract float getCharWidth(char c, boolean bold, boolean italic, float height);

	}

	class StandardAdapter extends FontAdapter {
		final FontStorage  fontStorage = ((TextRendererExt) baseRenderer).ext_fontStorage();

		@Override
		public float draw(char c, char kernChar, boolean bold, boolean italic, float x, float y, float height, BufferBuilder buffer, float red, float green, float blue, float alpha) {
			final Glyph glyph = fontStorage.getGlyph(c);
			final GlyphRenderer glyphRenderer = fontStorage.getGlyphRenderer(c);
			final Identifier glyphTexture = glyphRenderer.getId();
			Identifier lastGlyphTexture = null;

			if (glyphTexture != null) {
				if (lastGlyphTexture != glyphTexture) {
					tess.draw();
					textureManager.bindTexture(glyphTexture);
					buffer.begin(7, VertexFormats.POSITION_UV_COLOR);
					lastGlyphTexture = glyphTexture;
				}

				glyphRenderer.draw(textureManager, italic, x, y, buffer, red, green, blue, alpha);
				if (bold) {
					glyphRenderer.draw(textureManager, italic, x + glyph.getBoldOffset(), y, buffer, red, green, blue, alpha);
				}
			}

			return glyph.getAdvance(bold);
		}

		@Override
		protected float getCharWidth(char kernChar, char c, boolean bold, boolean italic, float height) {
			return fontStorage.getGlyph(c).getAdvance(bold);
		}

		@Override
		protected float getCharWidth(char c, boolean bold, boolean italic, float height) {
			return fontStorage.getGlyph(c).getAdvance(bold);
		}
	}

	class TrueTypeAdapter extends FontAdapter {
		final FontStorage  baseStorage = ((TextRendererExt) baseRenderer).ext_fontStorage();
		final FontStorage  boldStorage = ((TextRendererExt) boldRenderer).ext_fontStorage();
		final FontStorage  italicStorage = ((TextRendererExt) italicRenderer).ext_fontStorage();
		final FontStorage  boldItalicStorage = ((TextRendererExt) boldItalicRenderer).ext_fontStorage();

		Identifier lastGlyphTexture = null;

		@Override
		public float draw(char c, char kernChar, boolean bold, boolean italic, float x, float y, float height, BufferBuilder buffer, float red, float green, float blue, float alpha) {
			final FontStorage fontStorage;
			if (bold) {
				fontStorage = italic ? boldItalicStorage : boldStorage;
			} else {
				fontStorage = italic ? italicStorage : baseStorage;
			}

			final Glyph glyph = fontStorage.getGlyph(c);
			final GlyphRenderer glyphRenderer = fontStorage.getGlyphRenderer(c);
			final Identifier glyphTexture = glyphRenderer.getId();

			if (glyphTexture != null) {
				if (lastGlyphTexture != glyphTexture) {
					tess.draw();
					textureManager.bindTexture(glyphTexture);
					buffer.begin(7, VertexFormats.POSITION_UV_COLOR);
					lastGlyphTexture = glyphTexture;
				}

				glyphRenderer.draw(textureManager, false, x, y, buffer, red, green, blue, alpha);
			}

			return glyph.getAdvance(bold);
		}

		@Override
		protected float getCharWidth(char kernChar, char c, boolean bold, boolean italic, float height) {
			final FontStorage fontStorage;
			if (bold) {
				fontStorage = italic ? boldItalicStorage : boldStorage;
			} else {
				fontStorage = italic ? italicStorage : baseStorage;
			}
			return fontStorage.getGlyph(c).getAdvance();
		}

		@Override
		protected float getCharWidth(char c, boolean bold, boolean italic, float height) {
			final FontStorage fontStorage;
			if (bold) {
				fontStorage = italic ? boldItalicStorage : boldStorage;
			} else {
				fontStorage = italic ? italicStorage : baseStorage;
			}
			return fontStorage.getGlyph(c).getAdvance();
		}
	}

	public void drawMarkdown(List<String> lines, float x, float y, int color, float yOffset, float height) {
		GlStateManager.enableAlphaTest();

		if (lines == null || lines.isEmpty()) {
			return;
		} else {
			if ((color & 0xFC000000) == 0) {
				color |= 0xFF000000;
			}

			drawMarkdownInner(lines, x, y, color, yOffset, height);
		}
	}

	public void drawMarkdownInner(List<String> lines, float x, final float yIn, int color, float yOffset, float height) {
		final boolean rightToLeft = baseRenderer.isRightToLeft();
		final float baseX = x;
		final float baseRed = ((color >> 16) & 255) / 255.0F;
		final float baseGreen = ((color >> 8) & 255) / 255.0F;
		final float baseBlue = (color & 255) / 255.0F;
		final float red = baseRed;
		final float green = baseGreen;
		final float blue = baseBlue;
		final float alpha = (color >> 24 & 255) / 255.0F;
		final Tessellator tess = Tessellator.getInstance();
		final BufferBuilder buff = tess.getBufferBuilder();
		final float yMax = yIn + height;
		final float singleLine = style.lineHeight;
		final float singleLinePlus = style.lineHeightPlusHalf;
		final float indentWidth = adapter.indentWidth;
		final List<Rectangle> rects = this.rects;
		rects.clear();

		float y = yIn - yOffset;
		int bold = 0;
		int italic = 0;
		int underline = 0;
		int strikethru = 0;
		int indent = 0;
		float margin = 0;
		char kernChar = NOTHING;
		float lineHeight = singleLine;

		buff.begin(7, VertexFormats.POSITION_UV_COLOR);

		for (String text : lines) {
			if (rightToLeft) {
				text = baseRenderer.mirror(text);
			}

			for(int i = 0; i < text.length(); ++i) {
				final char c = text.charAt(i);

				switch(c) {

				case BOLD:
					if(bold++ == 0) {
						kernChar = NOTHING;
					}
					break;

				case BOLD_OFF:
					if(--bold == 0) {
						kernChar = NOTHING;
					}
					break;

				case STRIKETHROUGH:
					++strikethru;
					break;

				case STRIKETHROUGH_OFF:
					--strikethru;
					break;

				case UNDERLINE:
					++underline;
					break;

				case UNDERLINE_OFF:
					--underline;
					break;

				case ITALIC:
					if(italic++ == 0) {
						kernChar = NOTHING;
					}
					break;

				case ITALIC_OFF:
					if(--italic == 0) {
						kernChar = NOTHING;
					}
					break;

				case INDENT_PLUS:
					++indent;
					margin = indent * indentWidth * (baseRenderer.isRightToLeft() ? -1 : 1);
					break;

				case INDENT_MINUS:
					--indent;
					margin = indent * indentWidth * (baseRenderer.isRightToLeft() ? -1 : 1);
					break;

				case ALIGN_TO_INDENT:
					//because margin is always added, resetting to margin is resetting to base
					kernChar = NOTHING;
					x = baseX;
					break;

				case NEWLINE:
					lineHeight = singleLine;
					kernChar = NOTHING;
					break;

				case NEWLINE_PLUS_HALF:
					lineHeight = singleLinePlus;
					kernChar = NOTHING;
					break;

				default:
					if (y >= yIn && y + lineHeight <= yMax) {
						final float advance = adapter.draw(c, kernChar, bold > 0, italic > 0, margin + x, y, lineHeight, buff, red, green, blue, alpha);

						if (strikethru > 0) {
							rects.add(new Rectangle(margin + x, y + 4.5F, margin + x + advance, y + 4.5F - 1.0F, red, green, blue, alpha));
						}

						if (underline > 0) {
							rects.add(new Rectangle(margin + x, y + 9.0F, margin + x + advance, y + 9.0F - 1.0F, red, green, blue, alpha));
						}

						x += advance;
					}
				}
			}

			// PERF: early skip/exit for Y out of range
			y += lineHeight;
			kernChar = NOTHING;
			lineHeight = singleLine;
			x = baseX;
		}

		tess.draw();

		if (!rects.isEmpty()) {
			GlStateManager.disableTexture();
			buff.begin(7, VertexFormats.POSITION_COLOR);

			for (final Rectangle r :rects) {
				r.draw(buff);
			}

			tess.draw();
			GlStateManager.enableTexture();
		}
	}

	/**
	 * Returns total vertical height of lines that have already been split to a list.
	 */
	public float verticalHeight(List<String> lines) {
		float y = 0;

		final int singleLine = 9 + 2;
		final int singleLinePlus = singleLine + (singleLine >> 1);
		int lineHeight = singleLine;
		for (final String text : lines) {
			for(int i = 0; i < text.length(); ++i) {
				final char c = text.charAt(i);

				switch(c) {

				case NEWLINE:
					lineHeight = singleLine;
					break;

				case NEWLINE_PLUS_HALF:
					lineHeight = singleLinePlus;
					break;

				default:
					break;
				}
			}

			y += lineHeight;
			lineHeight = singleLine;
		}

		return y;
	}

	static class Rectangle {
		protected final float xMin;
		protected final float yMin;
		protected final float xMax;
		protected final float yMax;
		protected final float red;
		protected final float green;
		protected final float blue;
		protected final float alpha;

		private Rectangle(float xMin, float yMin, float xMax, float yMax, float red, float green, float blue, float alpha) {
			this.xMin = xMin;
			this.yMin = yMin;
			this.xMax = xMax;
			this.yMax = yMax;
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.alpha = alpha;
		}

		public void draw(BufferBuilder buffer) {
			buffer.vertex(xMin, yMin, 0.0D).color(red, green, blue, alpha).next();
			buffer.vertex(xMax, yMin, 0.0D).color(red, green, blue, alpha).next();
			buffer.vertex(xMax, yMax, 0.0D).color(red, green, blue, alpha).next();
			buffer.vertex(xMin, yMax, 0.0D).color(red, green, blue, alpha).next();
		}
	}
}
