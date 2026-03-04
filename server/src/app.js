console.log(">>> APP.JS HAS SUCCESSFULLY LOADED NEW BLUEPRINT <<<");

const express = require('express');
const path = require('path'); // <<< ADD THIS LINE
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
require('dotenv').config();

const app = express();
app.use(express.json());


// Middleware Filters
app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        // Allow Socket.io and WebRTC adapter from their CDNs
        scriptSrc: ["'self'", "https://cdn.socket.io", "https://webrtc.github.io", "'unsafe-inline'"],
        // Allow the browser to connect to your local Socket.io server
        connectSrc: ["'self'", "ws://localhost:3000", "http://localhost:3000"],
        // Allow the video element to receive data
        mediaSrc: ["'self'", "blob:"],
        // Required to allow the 'onclick' handlers on your buttons
        scriptSrcAttr: ["'unsafe-inline'"],
      },
    },
  })
);
app.use(cors());
app.use(morgan('combined'));
// This tells Express: "Go up one level from 'src', and look inside the 'public' folder"
app.use(express.static(path.join(__dirname, '../public')));
app.use('/api/command', require('./routes/commandRoutes'));
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