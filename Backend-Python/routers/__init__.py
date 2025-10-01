from fastapi import APIRouter
from .users import router as users_router
from .products import router as products_router
from .prescriptions import router as prescriptions_router
from .orders import router as orders_router
from .appointments import router as appointments_router
from .addresses import router as addresses_router
from .books import router as books_router
from .health_records import router as health_records_router
from .payments import router as payments_router
from .medicines import router as medicines_router

api_router = APIRouter()

# 注册所有路由
api_router.include_router(users_router)
api_router.include_router(products_router)
api_router.include_router(prescriptions_router)
api_router.include_router(orders_router)
api_router.include_router(appointments_router)
api_router.include_router(addresses_router)
api_router.include_router(books_router)
api_router.include_router(health_records_router)
api_router.include_router(payments_router)
api_router.include_router(medicines_router)