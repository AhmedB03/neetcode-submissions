class Solution:
    def longestConsecutive(self, nums: List[int]) -> int:
        if not nums:
            return 0
        
        res = {}
        nums.sort()

        for i in nums:
            res[i] = [i]

            for j in list(res.values()):
                
                if i - 1 in j and i not in j:
                    j.append(i)
        
        longest_list = max(list(res.values()), key=len)
        print(res.values())
        return len(longest_list)