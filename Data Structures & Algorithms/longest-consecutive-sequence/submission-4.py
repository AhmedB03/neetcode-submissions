class Solution:
    def longestConsecutive(self, nums: List[int]) -> int:
       if not nums:
        return 0
       numset = set(nums)
       longest = 0

       for i in numset:
        if i - 1 not in numset:
            leng = 1
            while i + leng in numset:
                leng += 1
            longest = max(longest,leng)
        
       
       return longest
    
    

    


      