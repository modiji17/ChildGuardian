const express = require('express');
const router = express.Router();

// Temporary in-memory storage (we'll add database later)
const devices = {};

router.post('/register', (req, res) => {
    const { deviceId, deviceName, manufacturer, model, androidVersion, sdkVersion } = req.body;
    if (!deviceId) {
        return res.status(400).json({ success: false, message: 'deviceId required' });
    }
    // Store or update
    devices[deviceId] = {
        deviceId,
        deviceName,
        manufacturer,
        model,
        androidVersion,
        sdkVersion,
        registeredAt: new Date()
    };
    console.log('Device registered:', devices[deviceId]);
    res.json({ success: true, message: 'Registered', deviceId });
});

module.exports = router;