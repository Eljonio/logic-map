const express = require('express')
const db = require('../db')
const { cacheGet, cacheSet } = require('../cache')

const router = express.Router()

router.get('/', async (req, res) => {
  const cached = await cacheGet('products:all')
  if (cached) return res.json(cached)
  const [rows] = await db.query('SELECT id, name, price FROM products')
  await cacheSet('products:all', rows)
  res.json(rows)
})

router.patch('/:id/price', async (req, res) => {
  const rate = await fetch(process.env.FX_API_URL + '/latest').then(r => r.json())
  await db.query('UPDATE products SET price = ? WHERE id = ?', [req.body.price * rate.value, req.params.id])
  res.status(204).end()
})

module.exports = router
