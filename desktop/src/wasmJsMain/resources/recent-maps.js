// The mind maps opened last in this browser. Each is kept in IndexedDB with its file handle, which opens the
// file again once the user allows it; a handle cannot be kept in local storage. Only browsers with the File
// System Access API give the page handles.
window.mindMapViewRecent = (() => {
    const MAX_COUNT = 10;
    const STORE = 'recent';
    const ENTRIES = 'entries';
    // { key, name, handle }, the most recent first. The key ends in the file's name, as the app shows it.
    let entries = [];

    function database() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open('mindmapview', 1);
            request.onupgradeneeded = () => request.result.createObjectStore(STORE);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    // The list still works for this visit when the browser keeps nothing, as in a private window.
    async function save() {
        try {
            const db = await database();
            await new Promise((resolve, reject) => {
                const transaction = db.transaction(STORE, 'readwrite');
                transaction.objectStore(STORE).put(entries, ENTRIES);
                transaction.oncomplete = resolve;
                transaction.onerror = () => reject(transaction.error);
            });
            db.close();
        } catch (e) {
        }
    }

    async function isSameFile(a, b) {
        try {
            return await a.isSameEntry(b);
        } catch (e) {
            return false;
        }
    }

    // Keys are joined by a character no file name has, for the app to split them again.
    const keys = () => entries.map(entry => entry.key).join('\u0000');

    return {
        isSupported: () => !!window.showOpenFilePicker && !!window.indexedDB,

        async load() {
            try {
                const db = await database();
                entries = await new Promise((resolve, reject) => {
                    const request = db.transaction(STORE).objectStore(STORE).get(ENTRIES);
                    request.onsuccess = () => resolve(request.result || []);
                    request.onerror = () => reject(request.error);
                });
                db.close();
            } catch (e) {
                entries = [];
            }
        },

        keys,

        // A file that is in the list already moves to the front.
        async add(file) {
            if (!file.handle) return keys();
            const others = [];
            for (const entry of entries) {
                if (!(await isSameFile(entry.handle, file.handle))) others.push(entry);
            }
            const entry = { key: crypto.randomUUID() + '/' + file.name, name: file.name, handle: file.handle };
            entries = [entry, ...others].slice(0, MAX_COUNT);
            await save();
            return keys();
        },

        async remove(key) {
            entries = entries.filter(entry => entry.key !== key);
            await save();
            return keys();
        },

        file(key) {
            const entry = entries.find(entry => entry.key === key);
            return entry ? { name: entry.name, handle: entry.handle } : { name: key.substring(key.indexOf('/') + 1) };
        },

        // After the page is loaded again, the browser asks the user before the file may be read or written.
        // It only asks while a click of the user's is recent, so this is called right after the menu is clicked.
        async access(key) {
            const entry = entries.find(entry => entry.key === key);
            if (!entry) return 'missing';
            try {
                const options = { mode: 'readwrite' };
                if (await entry.handle.queryPermission(options) !== 'granted'
                    && await entry.handle.requestPermission(options) !== 'granted') {
                    return 'denied';
                }
                await entry.handle.getFile();
                return 'available';
            } catch (e) {
                return e.name === 'NotFoundError' ? 'missing' : 'denied';
            }
        },
    };
})();
