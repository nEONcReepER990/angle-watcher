import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates the Angle Watcher mod icon: a dark rounded panel with a horizontal
 * compass tape — colored angle-range bands, degree ticks, caret needle, numeric
 * readout chip and a big translucent cardinal "N".
 */
public final class IconGen {
	public static void main(String[] args) throws Exception {
		Path outDir = Paths.get(args.length > 0 ? args[0] : ".");
		ImageIO.write(render(128), "png", outDir.resolve("icon.png").toFile());
		System.out.println("Wrote " + outDir.resolve("icon.png").toAbsolutePath());
	}

	/** @param size output canvas size in pixels (icon is square). */
	static BufferedImage render(int size) {
		float k = size / 128f; // design is laid out on a 128px grid
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.scale(k, k);

		// ---- background: deep navy panel with a subtle top sheen -------------------
		g.setPaint(new GradientPaint(0, 0, new Color(0x242B3B), 0, 128, new Color(0x121826)));
		g.fillRoundRect(0, 0, 128, 128, 24, 24);

		g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 34), 0, 64, new Color(255, 255, 255, 0)));
		g.fillRoundRect(0, 0, 128, 64, 24, 24);

		g.setColor(new Color(0, 0, 0, 90));
		g.fill(new RoundRectangle2D.Float(5.5f, 5.5f, 117, 117, 20, 20));
		g.setColor(new Color(255, 255, 255, 26));
		g.setStroke(new BasicStroke(1.6f));
		g.drawRoundRect(3, 3, 122, 122, 21, 21);

		// ---- cardinal ghost letter -------------------------------------------------
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 74));
		FontMetrics fm = g.getFontMetrics();
		String n = "N";
		int nx = (128 - fm.stringWidth(n)) / 2;
		int ny = 70 + fm.getAscent() / 2 - 2;
		g.setColor(new Color(255, 255, 255, 16));
		g.drawString(n, nx, ny + 2);
		g.setColor(new Color(255, 255, 255, 12));
		g.drawString(n, nx, ny);

		// ---- the tape ---------------------------------------------------------------
		int tx = 12, ty = 52, tw = 104, th = 26, r = 8;
		g.setComposite(AlphaComposite.SrcOver);
		g.setPaint(new GradientPaint(0, ty, new Color(0x0B0F17), 0, ty + th, new Color(0x161C2A)));
		g.fillRoundRect(tx, ty, tw, th, r, r);
		g.setColor(new Color(255, 255, 255, 40));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(new RoundRectangle2D.Float(tx + 0.5f, ty + 0.5f, tw - 1, th - 1, r, r));

		g.setClip(g.getClip()); // keep; explicit re-set below after bands
		java.awt.geom.Area tapeArea = new java.awt.geom.Area(
				new RoundRectangle2D.Float(tx, ty, tw, th, r, r));
		g.setClip(tapeArea);

		// angle-range bands (projected from a -180..180 dial onto the tape)
		band(g, tx, ty, th, -180, -120, 0x80FF5555);
		band(g, tx, ty, th, -60, -30, 0x8055FFFF);
		band(g, tx, ty, th, 30, 90, 0x80FFAA00);
		band(g, tx, ty, th, 120, 180, 0x8055FF55);

		// tick marks every 15 "degrees"
		g.setColor(new Color(255, 255, 255, 70));
		for (int deg = -180; deg <= 180; deg += 15) {
			float x = tx + (deg + 180f) / 360f * tw;
			boolean major = ((deg % 45) + 360) % 360 == 0;
			g.setStroke(new BasicStroke(major ? 1.6f : 1f));
			g.drawLine(Math.round(x), ty + th - (major ? 11 : 7), Math.round(x), ty + th - 2);
		}
		g.setClip(null);

		// crisp tape outline on top of bands/ticks
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(new Color(255, 255, 255, 40));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(new RoundRectangle2D.Float(tx + 0.5f, ty + 0.5f, tw - 1, th - 1, r, r));

		// caret needle + current-heading chip
		g.setColor(new Color(0xFFC24D));
		g.fillPolygon(
				new int[]{64, 59, 69},
				new int[]{ty + th + 7, ty + th - 3, ty + th - 3},
				3);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(0x000000));
		g.drawPolygon(
				new int[]{64, 59, 69},
				new int[]{ty + th + 7, ty + th - 3, ty + th - 3},
				3);

		g.setColor(new Color(0x0B0F17, true));
		g.fillRoundRect(41, 19, 46, 18, 6, 6);
		g.setColor(new Color(0xFFC24D));
		g.draw(new RoundRectangle2D.Float(41.5f, 19.5f, 45, 17, 6, 6));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		fm = g.getFontMetrics();
		String readout = "047\u00B0";
		g.setColor(new Color(0xFFE0A3));
		g.drawString(readout, 64 - fm.stringWidth(readout) / 2f, 19 + (18 + fm.getAscent() - fm.getDescent()) / 2f - 2);

		g.dispose();
		return img;
	}

	/** Draws one translucent angle-range band inside the (already clipped) tape. */
	private static void band(Graphics2D g, int tx, int ty, int th, double from, double to, int argb) {
		float x1 = tx + (float) ((from + 180.0) / 360.0) * 104;
		float x2 = tx + (float) ((to + 180.0) / 360.0) * 104;
		g.setColor(new Color(argb, true));
		g.fillRect(Math.round(x1), ty, Math.round(x2 - x1), th);
	}
}
