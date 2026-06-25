class Solution:
    def maxArea(self, heights: List[int]) -> int:
        if not heights:
            return 0
        
        maxHeight = 0
        l,r = 0, len(heights) - 1

        while r > l:
            width = r - l
            maxHeight = max(maxHeight,(width*min(heights[l],heights[r])))
            if heights[r] > heights[l]:
                l += 1
            else:
                
                r -= 1
        
        return maxHeight

