const express = require('express')
const db = require('../db')
const { authenticateToken, requireRole } = require('../auth')
const { io } = require('../realtime')
const { cacheInvalidate } = require('../cache')

const router = express.Router()

async function loadItems(orderId) {
  const [rows] = await db.query('SELECT product_id, qty FROM order_items WHERE order_id = ?', [orderId])
  return rows
}

router.get('/:id', authenticateToken, async (req, res) => {
  const { id } = req.params
  const [rows] = await db.query('SELECT id, total, status FROM orders WHERE id = ?', [id])
  const items = await loadItems(id)
  res.json({ ...rows[0], items })
})

router.post('/', authenticateToken, async (req, res) => {
  const { customerId, total } = req.body
  const conn = await db.getConnection()
  await conn.beginTransaction()
  const [result] = await conn.query('INSERT INTO orders (customer_id, total, status) VALUES (?, ?, ?)', [customerId, total, 'new'])
  await conn.commit()
  io.emit('order:created', { id: result.insertId })
  cacheInvalidate('orders:recent')
  res.status(201).json({ id: result.insertId })
})

router.delete('/:id', authenticateToken, requireRole('admin'), async (req, res) => {
  await db.query('DELETE FROM orders WHERE id = ?', [req.params.id])
  res.status(204).end()
})

module.exports = router
