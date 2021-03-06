package spriteeditor;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import static spriteeditor.SpriteUnpacker.FOLDER;

/**
 * Packs the sprites into a
 * compressed archive.
 */
public class SpritePacker {
	/**
	 * The sprites currently loaded
	 */
	private TreeMap<Integer, Sprite> sprites = new TreeMap<Integer, Sprite>();

	/**
	 * Constructs a new sprite packer with the given file
	 *
	 * @param file the file to output the sprites to
	 */
	private SpritePacker(File file) {
		// Open the .pak archive and put all Sprites into a Map
		if (file.exists())
			sprites = readZip(file);
		else
			sprites = null;

		try {
			if (!file.exists())
				if (file.createNewFile())
					file = file.getAbsoluteFile();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Loop through all the Sprites and save a PNG version of them
		// The sprite directory to import new sprites from
		String NEW_DIR = "./unpack/";
		File newSprites = new File(NEW_DIR);
		String[] spriteList = newSprites.list();
		Sprite created = null;

		if (sprites == null)
			sprites = new TreeMap<Integer, Sprite>();

		int count = 0;
		final int total = spriteList.length;

		for (String s : spriteList) {
			if (!s.endsWith(".spr"))
				continue;

			try {
				created = Sprite.deserializeFrom(new File(NEW_DIR + s));
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

			int id = Integer.parseInt(s.replaceAll(".spr", ""));

			for (Sprite sprite : getSprites()) {
				try {
					if (sprite == null) {
						System.out.println("***** ERROR: NULL SPRITE. PAK CORRUPT? *****");
						continue;
					}

					if (id == sprite.getID()) {
						System.out.println("Found clashing IDs: " + id + ". Removing old sprite.");
						sprites.remove(sprite);
					}

				} catch (ClassCastException ignored) {
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			sprites.put(id, created);

			int intProg = (int) (((double) ++count / (double) total) * 100D);

			if (intProg <= 0)
				intProg = 1;

			String progress = String.valueOf(intProg);

			if (intProg < 10)
				progress = "0" + progress + "%";
			else
				progress = progress + "%";

			if (intProg == 100)
				progress = "99% - packing";

			System.out.println("\n\n == Progress: " + progress + " ==\n\n");
		}

		System.out.println("Packing a total of " + sprites.size() + " sprites into " + file.getName() + ".");

		writeZip(sprites, file);

		System.out.println("\n\n == Progress: 100% - finished ==\n\n");
		System.exit(0);
	}

	/**
	 * The entry point
	 */
	public static void main(String[] args) {
		if (!new File(FOLDER + "unpack").exists())
			new File(FOLDER).mkdir();

		JFileChooser fileChooser = new JFileChooser();
		JDialog dialog = new JDialog();

		fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
		fileChooser.setFileFilter(new FileNameExtensionFilter("Open RSC (.orsc)", "orsc"));
		int result = fileChooser.showOpenDialog(dialog);
		if (result == JFileChooser.APPROVE_OPTION) {
			try {
				new SpritePacker(fileChooser.getSelectedFile());
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
		} else {
			System.out.println("Cancelled");
			System.exit(0);
		}
	}

	/**
	 * Saves the given image into the given format into the given file
	 *
	 * @param image the image to save
	 * @param file  the file to save to
	 */
	static void saveImage(BufferedImage image, File file) throws IOException {
		ImageIO.write(image, "png", file);
	}

	/**
	 * Returns a ByteBuffer containing everything available from the given InputStream
	 */
	static ByteBuffer streamToBuffer(BufferedInputStream in) throws IOException {
		byte[] buffer = new byte[in.available()];
		in.read(buffer, 0, buffer.length);
		return ByteBuffer.wrap(buffer);
	}

	/**
	 * Fully loads an Image into memory then converts to a BufferedImage
	 */
	public static BufferedImage loadImage(File file) throws IOException {
		Image image = new ImageIcon(Toolkit.getDefaultToolkit().getImage(file.getAbsolutePath())).getImage();
		BufferedImage buffImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);

		Graphics g = buffImage.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();

		return buffImage;
	}

	/**
	 * @return the list of sprites
	 */
	private Collection<Sprite> getSprites() {
		return sprites.values();
	}

	/**
	 * Loads the sprites from the given file
	 *
	 * @param file the pak file to load
	 */
	private TreeMap<Integer, Sprite> readZip(File file) {
		try {
			TreeMap<Integer, Sprite> sprites = new TreeMap<Integer, Sprite>();

			if (file == null)
				file.createNewFile();

			ZipFile zip = new ZipFile(file);

			for (Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				BufferedInputStream in = new BufferedInputStream(zip.getInputStream(entry));
				ByteBuffer buffer = streamToBuffer(in);
				in.close();

				Sprite sprite = Sprite.unpack(buffer);
				sprite.setName(Integer.parseInt(entry.getName()), "");
				sprites.put(Integer.parseInt(entry.getName()), sprite);
			}

			return sprites;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return null;
		}
	}

	/**
	 * @return if the file output was successful
	 */
	private void writeZip(TreeMap<Integer, Sprite> sprites, File file) {
		try {
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(new File(file.toString().replaceAll(".pak", "_NEW.pak"))));
			out.setLevel(9);

			for (Entry<Integer, Sprite> e : sprites.entrySet()) {
				String name = String.valueOf(e.getKey());
				Sprite sprite = e.getValue();

				out.putNextEntry(new ZipEntry(name));
				out.write(sprite.pack().array());
				out.closeEntry();
			}

			out.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
}
