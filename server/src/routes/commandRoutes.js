const express = require('express');
const router = express.Router();

// In-memory queue for demonstration
router.post('/send', (req, res) => {
    const { deviceId, command } = req.body;
    if (!deviceId || !command) {
        return res.status(400).json({ error: 'Missing deviceId or command' });
    }
    // Emit command to the device via socket.io
    const io = req.app.get('io'); // need to store io instance in app
    // We turn the object into a text string so Android can definitely read it
    io.to(deviceId).emit('command', JSON.stringify(command));
    res.json({ success: true });
});

module.exports = router;