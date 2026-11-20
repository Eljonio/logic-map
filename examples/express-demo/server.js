// Fictional demo app used to try the Logic Map scanner. It is never run.
const express = require('express')
const orders = require('./routes/orders')
const products = require('./routes/products')

const app = express()
app.use(express.json())
app.use('/api/orders', orders)
app.use('/api/products', products)

app.listen(process.env.PORT || 3000)
