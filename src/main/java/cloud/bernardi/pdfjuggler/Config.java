/*
 * Copyright (C) 2020 Paolo Bernardi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cloud.bernardi.pdfjuggler;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent application configuration.
 *
 * @author rnd
 */
public class Config {

    private int thumbWidth = MainFrame.DEFAULT_THUMB_WIDTH;
    private int thumbHeight = MainFrame.DEFAULT_THUMB_HEIGHT;
    private Rectangle bounds;
    private File lastOpenDir;

    private File getFile() {
        String home = System.getProperty("user.home");
        return new File(home, ".pdfjuggler.properties");
    }

    /**
     * Load the application configuration file (if any) and apply the settings
     * to the MainFrame.
     *
     * @param window
     */
    public void load(MainFrame window) {
        File configFile = getFile();
        if (!configFile.exists()) {
            return;
        }
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(configFile)) {
            prop.load(input);
            String x = prop.getProperty("x");
            String y = prop.getProperty("y");
            String width = prop.getProperty("width");
            String height = prop.getProperty("height");
            if (x != null && y != null && width != null && height != null) {
                bounds = new Rectangle(
                        Integer.parseInt(x),
                        Integer.parseInt(y),
                        Integer.parseInt(width),
                        Integer.parseInt(height)
                );
                window.setBounds(bounds);
            }
            String lastOpenDirStr = prop.getProperty("lastOpenDir");
            if (lastOpenDirStr != null) {
                lastOpenDir = new File(lastOpenDirStr);
                if (lastOpenDir.exists()) {
                    window.lastOpenDir = lastOpenDir;
                }
            }
            String thumbWidthStr = prop.getProperty("thumbWidth");
            String thumbHeightStr = prop.getProperty("thumbHeight");
            if (thumbWidthStr != null && thumbHeightStr != null) {
                window.thumbWidth = Integer.parseInt(thumbWidthStr);
                window.thumbHeight = Integer.parseInt(thumbHeightStr);
            }
        } catch (IOException ex) {
            Logger.getLogger(Config.class.getName()).log(Level.WARNING, null, ex);
        }
    }

    /**
     * Set the in-memory configuration from the MainFrame status.
     *
     * @param window
     */
    public void set(MainFrame window) {
        if (!window.isMaximized) {
            bounds = window.getBounds();
        }
        lastOpenDir = window.lastOpenDir;
        thumbWidth = window.thumbWidth;
        thumbHeight = window.thumbHeight;
    }

    /**
     * Save the in-memory configuration to a file.
     *
     * @param window
     */
    public void save(MainFrame window) {
        set(window);
        Properties prop = new Properties();
        if (bounds != null) {
            prop.setProperty("x", "" + bounds.x);
            prop.setProperty("y", "" + bounds.y);
            prop.setProperty("width", "" + bounds.width);
            prop.setProperty("height", "" + bounds.height);
        }
        if (lastOpenDir != null) {
            prop.setProperty("lastOpenDir", lastOpenDir.getAbsolutePath());
        }
        prop.setProperty("thumbWidth", "" + thumbWidth);
        prop.setProperty("thumbHeight", "" + thumbHeight);
        try (OutputStream output = new FileOutputStream(getFile())) {
            prop.store(output, null);
        } catch (IOException ex) {
            Logger.getLogger(Config.class.getName()).log(Level.WARNING, null, ex);
        }
    }
}
