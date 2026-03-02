console.log(">>> APP.JS HAS SUCCESSFULLY LOADED NEW BLUEPRINT <<<");

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
require('dotenv').config();

const app = express();

// Middleware Filters
app.use(helmet());
app.use(cors());
app.use(morgan('combined'));
app.use(express.json());

// --- DIRECT INJECTION ROUTE (Bypassing external files) ---
const devices = {}; // In-memory database

app.post('/api/device/register', (req, res) => {
    console.log(' \n>>> IGNITION! DIRECT ROUTE HIT! <<< ');
    console.log('Received Data:', req.body);

    const { deviceId, deviceName, manufacturer, model, androidVersion, sdkVersion } = req.body;

    if (!deviceId) {
        return res.status(400).json({ success: false, message: 'deviceId required' });
    }

    devices[deviceId] = {
        deviceId, deviceName, manufacturer, model, androidVersion, sdkVersion,
        registeredAt: new Date()
    };

    console.log('Device officially registered in memory!');
    res.json({ success: true, message: 'Registered', deviceId });
});
// ---------------------------------------------------------

// Test route
app.get('/', (req, res) => {
  res.send('ChildGuardian Backend is running!');
});

module.exports = app;