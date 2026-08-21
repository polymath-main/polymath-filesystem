const fs = require('fs/promises');
const path = require('path');

async function listDir(dirPath) {
    const items = await fs.readdir(dirPath, { withFileTypes: true });
    
    return Promise.all(items.map(async (item) => {
        const fullPath = path.join(dirPath, item.name);
        try {
            const stats = await fs.stat(fullPath);
            return {
                name: item.name,
                path: fullPath,
                isDirectory: item.isDirectory(),
                size: stats.size,
                mtime: stats.mtime,
                extension: path.extname(item.name).toLowerCase()
            };
        } catch (e) {
            // Handle broken symlinks or permission errors silently for the list
            return {
                name: item.name,
                path: fullPath,
                isDirectory: item.isDirectory(),
                size: 0,
                mtime: null,
                extension: path.extname(item.name).toLowerCase(),
                error: e.message
            };
        }
    }));
}

module.exports = {
    listDir
};
