class Solution:
    def productExceptSelf(self, nums: List[int]) -> List[int]:
        n = len(nums)
        res = [1] * n

        left_running_product = 1
        
        for i in range(n):
            res[i] = left_running_product
            left_running_product *= nums[i]
        
        right_running_product = 1

        for i in range(n - 1,-1,-1):
            res[i] *= right_running_product
            right_running_product *= nums[i]
        
        return res