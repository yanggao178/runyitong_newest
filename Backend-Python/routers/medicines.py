from fastapi import APIRouter, HTTPException, Query
from typing import Dict, Any, Optional

from ai.ai_prescription import query_medicine_info_simple

router = APIRouter(
    tags=["medicines"],
    responses={404: {"description": "Not found"}},
)


@router.get("/query", response_model=Dict[str, Any])
async def query_medicine(
    name: str = Query(..., description="药品名称或关键词"),
) -> Dict[str, Any]:
    """
    查询药品信息
    
    根据提供的药品名称或关键词，使用DeepSeek AI查询药品的详细信息，
    包括药品名称、主要成分、适应症、用法用量、不良反应等信息。
    
    Args:
        name: 药品名称或关键词
    
    Returns:
        Dict[str, Any]: 包含药品信息的字典
        
    Raises:
        HTTPException: 当查询失败时抛出异常
    """
    try:
        # 参数验证
        if not name or not name.strip():
            raise HTTPException(status_code=400, detail="药品名称不能为空")
        
        # 使用AI查询药品信息
        result = query_medicine_info_simple(name.strip())
        
        # 检查查询结果
        if not result.get("success"):
            # 即使查询不成功，也返回可用信息，同时包含错误提示
            return result
        
        return result
    
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"药品查询失败: {str(e)}")


@router.get("/search", response_model=Dict[str, Any])
async def search_medicines(
    keyword: str = Query(..., description="搜索关键词"),
) -> Dict[str, Any]:
    """
    搜索药品信息（简化版本）
    
    使用关键词搜索药品信息，提供基本的药品数据。
    
    Args:
        keyword: 搜索关键词
    
    Returns:
        Dict[str, Any]: 搜索结果
    """
    # 直接复用query_medicine功能
    return await query_medicine(name=keyword)