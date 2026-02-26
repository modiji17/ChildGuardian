const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
require('dotenv').config();

const app = express();

// Middleware
app.use(helmet());
app.use(cors());
app.use(morgan('combined'));
app.use(express.json());

// Test route
app.get('/', (req, res) => {
  res.send('ChildGuardian Backend is running!');
});

// API routes will be added later
// app.use('/api/devices', require('./routes/deviceRoutes'));

module.exports = app;